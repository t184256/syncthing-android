package com.nutomic.syncthingandroid.syncthing;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.nutomic.syncthingandroid.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Provides functions to interact with the syncthing REST API.
 */
public class RestApi implements SyncthingService.OnWebGuiAvailableListener {

	private static final String TAG = "RestApi";

	/**
	 * Parameter for {@link #getValue} or {@link #setValue} referring to "options" config item.
	 */
	public static final String TYPE_OPTIONS = "Options";

	/**
	 * Parameter for {@link #getValue} or {@link #setValue} referring to "gui" config item.
	 */
	public static final String TYPE_GUI = "GUI";

	private static final int NOTIFICATION_RESTART = 2;

	private Context mContext;

	private String mVersion;

	private String mUrl;

	private String mApiKey;

	private JSONObject mConfig;

	private NotificationManager mNotificationManager;

	public RestApi(Context context, String url, String apiKey) {
		mContext = context;
		mUrl = url;
		mApiKey = apiKey;
		mNotificationManager = (NotificationManager)
				mContext.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	/**
	 * Returns the full URL of the web gui.
	 */
	public String getUrl() {
		return mUrl;
	}

	@Override
	public void onWebGuiAvailable() {
		new GetTask() {
			@Override
			protected void onPostExecute(String version) {
				mVersion = version;
				Log.i(TAG, "Syncthing version is " + mVersion);
			}
		}.execute(mUrl, GetTask.URI_VERSION, mApiKey);
		new GetTask() {
			@Override
			protected void onPostExecute(String config) {
				try {
					mConfig = new JSONObject(config);
				}
				catch (JSONException e) {
					Log.w(TAG, "Failed to parse config", e);
				}
			}
		}.execute(mUrl, GetTask.URI_CONFIG, mApiKey);
	}

	/**
	 * Returns the version name, or a (text) error message on failure.
	 */
	public String getVersion() {
		return mVersion;
	}

	/**
	 * Stops syncthing. You should probably use SyncthingService.stopService() instead.
	 */
	public void shutdown() {
		mNotificationManager.cancel(NOTIFICATION_RESTART);
		new PostTask().execute(mUrl, PostTask.URI_SHUTDOWN, mApiKey, "");
	}

	/**
	 * Restarts the syncthing binary.
	 */
	public void restart() {
		new PostTask().execute(mUrl, PostTask.URI_RESTART, "");
	}

	/**
	 * Gets a value from config,
	 *
	 * Booleans are returned as {@link }Boolean#toString}, arrays as space seperated string.
	 *
	 * @param name {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
	 * @param key The key to read from.
	 * @return The value as a String, or null on failure.
	 */
	public String getValue(String name, String key) {
		try {
			Object value = mConfig.getJSONObject(name).get(key);
			return (value instanceof JSONArray)
					? ((JSONArray) value).join(" ").replace("\"", "")
					: String.valueOf(value);
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to get value for " + key, e);
			return null;
		}
	}

	/**
	 * Sets a value to config and sends it via Rest API.
	 *
	 * Booleans must be passed as {@link Boolean}, arrays as space seperated string
	 * with isArray true.
	 *
	 * @param name {@link #TYPE_OPTIONS} or {@link #TYPE_GUI}
	 * @param key The key to write to.
	 * @param value The new value to set, either String, Boolean or Integer.
	 * @param isArray True iff value is a space seperated String that should be converted to array.
	 */
	public <T> void setValue(String name, String key, T value, boolean isArray) {
		try {
			if (isArray) {
				JSONArray json = new JSONArray();
				for (String s : ((String) value).split(" ")) {
					json.put(s);
				}
				mConfig.getJSONObject(name).put(key, json);
			}
			else {
				mConfig.getJSONObject(name).put(key, value);
			}
			configUpdated();
		}
		catch (JSONException e) {
			Log.w(TAG, "Failed to set value for " + key, e);
		}
	}

	/**
	 * Sends the updated mConfig via Rest API to syncthing and displays a "restart" notification.
	 */
	private void configUpdated() {
		new PostTask().execute(mUrl, PostTask.URI_CONFIG, mConfig.toString());

		Intent i = new Intent(mContext, SyncthingService.class)
				.setAction(SyncthingService.ACTION_RESTART);
		PendingIntent pi = PendingIntent.getService(mContext, 0, i, 0);

		Notification n = new NotificationCompat.Builder(mContext)
				.setContentTitle(mContext.getString(R.string.restart_notif_title))
				.setContentText(mContext.getString(R.string.restart_notif_text))
				.setSmallIcon(R.drawable.ic_launcher)
				.setContentIntent(pi)
				.build();
		n.flags |= Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
		mNotificationManager.notify(NOTIFICATION_RESTART, n);
	}

}