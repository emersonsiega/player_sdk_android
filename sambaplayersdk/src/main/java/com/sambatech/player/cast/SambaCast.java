package com.sambatech.player.cast;

// import android.app.MediaRouteButton;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import com.google.android.gms.cast.framework.AppVisibilityListener;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.sambatech.player.R;
import com.sambatech.player.event.SambaCastListener;
import com.sambatech.player.utils.SharedPrefsUtils;

import java.io.IOException;

/**
 * Manages Chromecast integration.
 *
 * It must have a 1-to-1 relationship with activities to avoid memory leakage.
 * It could not be a Singleton, for example, static objects cannot hold Context references
 * (like widgets, resources, etc.), they must die together with their activity.<br><br>
 *
 * There are some steps to integrate:<br><br>
 *
 * 1. Initialize inside "onCreate" state<br>
 * 2. Notify "onResume" state<br>
 * 3. Notify "onPause" state<br>
 * 4. Pass the instance to the SambaPlayer
 *
 * @author Leandro Zanol on 23/03/17
 */
public final class SambaCast {

	private final CastStateListener stateListener = new CastStateListener() {
		@Override
		public void onCastStateChanged(int i) {
			Log.i("cast", "state: " + i);
		}
	};

	private final AppVisibilityListener appVisibilityListener = new AppVisibilityListener() {
		@Override
		public void onAppEnteredForeground() {
			Log.i("cast", "foreground");
		}

		@Override
		public void onAppEnteredBackground() {
			Log.i("cast", "background");
		}
	};

	private final SessionManagerListener<CastSession> sessionManagerListener = new SessionManagerListener<CastSession>() {
		@Override
		public void onSessionStarting(CastSession castSession) {
			Log.i("cast", "starting");
		}

		@Override
		public void onSessionStarted(CastSession castSession, String s) {
			Log.i("cast", "started " + s);
			onApplicationConnected(castSession);
		}

		@Override
		public void onSessionStartFailed(CastSession castSession, int i) {
			Log.i("cast", "start failed " + i);
			onApplicationDisconnected();
		}

		@Override
		public void onSessionEnding(CastSession castSession) {
			Log.i("cast", "ending");
		}

		@Override
		public void onSessionEnded(CastSession castSession, int i) {
			Log.i("cast", "ended");
			onApplicationDisconnected();
		}

		@Override
		public void onSessionResuming(CastSession castSession, String s) {
			Log.i("cast", "resuming " + s);
		}

		@Override
		public void onSessionResumed(CastSession castSession, boolean b) {
			Log.i("cast", "resumed " + b);
			onApplicationConnected(castSession);
		}

		@Override
		public void onSessionResumeFailed(CastSession castSession, int i) {
			Log.i("cast", "resume failed " + i);
			onApplicationDisconnected();
		}

		@Override
		public void onSessionSuspended(CastSession castSession, int i) {
			Log.i("cast", "suspended " + i);
		}

		private void onApplicationConnected(CastSession castSession) {
			if (listener != null)
				listener.onConnected(castSession);
		}

		private void onApplicationDisconnected() {
			SambaCast.cleanCacheDatas(SambaCast.this.context.getApplicationContext());
			if (listener != null)
				listener.onDisconnected();
		}
	};
	private Context context;

	private SessionManager sessionManager;
	private CastContext castContext;
	private SambaCastListener listener;
	private boolean isCastButtonOut;

	/**
	 * Initializes Chromecast SDK.
	 * Must be called inside "Activity.onCreate".
	 */
	public SambaCast(@NonNull Context context) {
		int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context);

		// if Google Play Services API is up-to-date
		if (status != ConnectionResult.SUCCESS)
			return;

		this.context = context;
		castContext = CastContext.getSharedInstance(context);
		sessionManager = castContext.getSessionManager();
	}

	/**
	 * Sets the listener to handle cast events.
	 * To remove pass null.
	 * @param listener The instance of the listener
	 */
	public void setEventListener(SambaCastListener listener) {
		this.listener = listener;
	}

	/**
	 * Called within "Activity.onResume()".
	 */
	public void notifyActivityResume() {
		if (castContext != null && sessionManager != null) {
			castContext.addCastStateListener(stateListener);
			castContext.addAppVisibilityListener(appVisibilityListener);
			sessionManager.addSessionManagerListener(sessionManagerListener, CastSession.class);
		}
	}

	/**
	 * Called within "Activity.onPause()".
	 */
	public void notifyActivityPause() {
		if (castContext != null && sessionManager != null) {
			castContext.removeCastStateListener(stateListener);
			castContext.removeAppVisibilityListener(appVisibilityListener);
			sessionManager.removeSessionManagerListener(sessionManagerListener, CastSession.class);
		}
	}

	/**
	 * Sends a pause message to the remote player.
	 */
	public void pauseCast(){
		String request = "{\"type\": \"pause\"}";
		sendRequest(request);
	}

	/**
	 * Sends a play message to the remote player.
	 */
	public void playCast(){
		String request = "{\"type\": \"play\"}";
		sendRequest(request);
	}

	/**
	 * Sends a seek message to the remote player.
	 */
	public void seekTo(int posisiton){
		String seekRequest = String.format("{\"type\": \"seek\", \"data\": %s }", posisiton/1000);
		sendRequest(seekRequest);
	}

	/**
	 * Sends a seek message to the remote player.
	 */
	public void changeSubtitle(String lang){
		String data = String.format("{\"lang\": \"%s\"}", lang);
		if(lang == null) {
			data = "{\"lang\": \"none\"}";
		}
		String seekRequest = String.format("{\"type\": \"changeSubtitle\", \"data\": %s }", data);
		sendRequest(seekRequest);

	}

	private void sendRequest(String request) {
		if(hasMediaSession(true)) {
			sessionManager.getCurrentCastSession().sendMessage(CastOptionsProvider.CUSTOM_NAMESPACE, request).setResultCallback(new ResultCallbacks<Status>() {
				@Override
				public void onSuccess(@NonNull Status status) {
					Log.i("message", "Message Sent OK: namespace:" + CastOptionsProvider.CUSTOM_NAMESPACE + " message:" + CastOptionsProvider.CUSTOM_NAMESPACE);
				}
				@Override public void onFailure(@NonNull Status status) {
					Log.i("message", "Sending message failed");}
				});
		}
	}


	/**
	 * Enables/Disables listening for remote progress event.
	 */
	public void registerDeviceForProgress(boolean register){
		String registerRequest = String.format("{\"type\": \"registerForProgressUpdate\", \"data\": %s }", register);
		if(hasMediaSession(true)) {
				sessionManager.getCurrentCastSession().sendMessage(CastOptionsProvider.CUSTOM_NAMESPACE, registerRequest).setResultCallback(new ResultCallbacks<Status>() {
					@Override
					public void onSuccess(@NonNull Status status) {
						Log.i("message", "Message Sent OK: namespace:" + CastOptionsProvider.CUSTOM_NAMESPACE + " message:" + CastOptionsProvider.CUSTOM_NAMESPACE);
					}

					@Override
					public void onFailure(@NonNull Status status) {
						Log.i("message", "Sending message failed");
					}
				});
		}
	}

	/**
	 * Sends a stop message to the remote player.
	 */
	public void stopCasting(){
        sessionManager.endCurrentSession(true);
		cleanCacheDatas(context);
    }

	/**
	 * Whether remote player is casting content or not.
	 */
    public boolean isCasting(){
		if(sessionManager!=null&&sessionManager.getCurrentCastSession()!=null) {
			return sessionManager.getCurrentCastSession().isConnected() || sessionManager.getCurrentCastSession().isConnecting();
		} else {
			return false;
		}
	}

	/**
	 * Sends a mute setup message to the remote player.
	 */
	public void setMute(boolean mute){
		try {
			sessionManager.getCurrentCastSession().setMute(mute);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Sends a volume setup message to the remote player.
	 */
	public void setVolume(double volume){
		//sessionManager.getCurrentCastSession().getRemoteMediaClient().setStreamVolume(volume);
		try {
			sessionManager.getCurrentCastSession().setVolume(volume);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns a cast session.
	 */
	public CastSession getCastSession(){
		return sessionManager.getCurrentCastSession();
	}

	/**
	 * Whether a session exists or not.
	 */
	private boolean hasMediaSession(boolean validateCastConnectingState) {
		if (sessionManager.getCurrentCastSession() == null) {
			return false;
		}
		boolean isCastSessionValid = sessionManager.getCurrentCastSession().isConnected();
		if (validateCastConnectingState) {
			boolean isCastSessionInConnectingMode = sessionManager.getCurrentCastSession().isConnecting();
			if (isCastSessionInConnectingMode) {
				return false; // no session to work with
			}
		}
		return isCastSessionValid;
	}

	public CastContext getCastContext() {
		return castContext;
	}

	@Nullable
	public static String currentMediaCastingId(Context context) {
		return SharedPrefsUtils.getStringPreference(context, SharedPrefsUtils.SharedPrefsKeys.MEDIA_CASTING_KEY);
	}

	public static void cleanCacheDatas(Context context) {
		SharedPrefsUtils.clearPreferenceByKey(context, SharedPrefsUtils.SharedPrefsKeys.MEDIA_CASTING_KEY);
		SharedPrefsUtils.clearPreferenceByKey(context, SharedPrefsUtils.SharedPrefsKeys.PLAYER_STATUS);
	}

	public static void setCurrentMediaCastingId(Context context, String mediaId) {
		SharedPrefsUtils.setStringPreference(context, SharedPrefsUtils.SharedPrefsKeys.MEDIA_CASTING_KEY, mediaId);
	}

	public static void setCurrentStatus(Context context, boolean playWhenReady) {
		SharedPrefsUtils.setBooleanPreference(context, SharedPrefsUtils.SharedPrefsKeys.PLAYER_STATUS, playWhenReady);
	}

	public static boolean getCurrentStatus(Context context) {
		return SharedPrefsUtils.getBooleanPreference(context, SharedPrefsUtils.SharedPrefsKeys.PLAYER_STATUS);
	}

    public boolean isCastButtonOut() {
        return isCastButtonOut;
    }

    public void setCastButtonOut(boolean castButtonOut) {
        isCastButtonOut = castButtonOut;
    }
}
