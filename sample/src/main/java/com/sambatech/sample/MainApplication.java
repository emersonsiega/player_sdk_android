package com.sambatech.sample;

import android.content.Context;
import android.support.multidex.MultiDexApplication;

import com.sambatech.player.offline.SambaDownloadManager;
import com.sambatech.sample.utils.Helpers;

public class MainApplication extends MultiDexApplication {
    private static MainApplication _instance;
	private static String _externalIp = "";

    @Override
    public void onCreate() {
        super.onCreate();

        _instance = this;

	    loadExternalIp();

		SambaDownloadManager.getInstance().init(this);
    }

    public static Context getAppContext() {
        return _instance.getApplicationContext();
    }

	public static String getExternalIp() {
		return _externalIp;
	}

	private void loadExternalIp() {
		Helpers.requestUrl("https://api.ipify.org", new Helpers.Callback() {
			@Override
			public void call(String response) {
				if (response == null) return;
				_externalIp = response.trim();
			}
		});
	}
}