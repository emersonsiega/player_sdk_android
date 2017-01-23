package com.sambatech.player;

import android.app.Activity;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.exoplayer.ExoPlayer;
import com.google.android.libraries.mediaframework.exoplayerextensions.ExoplayerWrapper;
import com.google.android.libraries.mediaframework.exoplayerextensions.Video;
import com.google.android.libraries.mediaframework.layeredvideo.PlaybackControlLayer;
import com.google.android.libraries.mediaframework.layeredvideo.SimpleVideoPlayer;
import com.sambatech.player.adapter.CaptionAdapter;
import com.sambatech.player.adapter.OutputAdapter;
import com.sambatech.player.event.SambaEvent;
import com.sambatech.player.event.SambaEventBus;
import com.sambatech.player.event.SambaPlayerListener;
import com.sambatech.player.model.SambaMedia;
import com.sambatech.player.model.SambaMediaConfig;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller for SambaPlayer view.
 *
 * @author Leandro Zanol - 7/12/15
 */
public class SambaPlayerController implements SambaPlayer {

	private SimpleVideoPlayer player;
	private SambaMediaConfig media = new SambaMediaConfig();
	private Timer progressTimer;
	private boolean _hasStarted;
	private boolean _hasFinished;
	private FrameLayout view;
	private OrientationEventListener orientationEventListener;
	private View outputMenu;
    private View captionMenu;
	private boolean autoFsMode;
	private boolean enableControls;

	private final ExoplayerWrapper.PlaybackListener playbackListener = new ExoplayerWrapper.PlaybackListener() {
		@Override
		public void onStateChanged(boolean playWhenReady, int playbackState) {
			Log.i("player", "state: " + playWhenReady + " " + playbackState);

			switch (playbackState) {
				case ExoPlayer.STATE_READY:
					if (playWhenReady) {
                        if (!_hasStarted) {
                            _hasStarted = true;
                            SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.START));

	                        //Show controls
							player.show();
                        }

                        SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PLAY));
                        startProgressTimer();
                    }
                    else {
						stopProgressTimer();
						SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PAUSE));
					}
					break;
				case ExoPlayer.STATE_ENDED:
					if (!playWhenReady)
						break;

					stopProgressTimer();
					pause();
					seek(0);
					SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FINISH));
					_hasFinished = true;
					break;
			}
		}

		@Override
		public void onError(Exception e) {
			Log.i("player", "Error: " + media, e);
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.ERROR, e.getMessage()));
		}

		@Override
		public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
			//Log.i("asdfg", unappliedRotationDegrees+" "+width + " " + height);
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.RESIZE, width, height, unappliedRotationDegrees, pixelWidthHeightRatio));
		}
	};

	private final PlaybackControlLayer.PlayCallback playListener = new PlaybackControlLayer.PlayCallback() {
		@Override
		public void onPlay() {
			if (player.getPlaybackState() == ExoPlayer.STATE_ENDED)
				seek(0);
		}
	};

	private final PlaybackControlLayer.FullscreenCallback fullscreenListener = new PlaybackControlLayer.FullscreenCallback() {
		@Override
		public void onGoToFullscreen() {
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FULLSCREEN));
		}

		@Override
		public void onReturnFromFullscreen() {
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.FULLSCREEN_EXIT));
		}
	};

	private final AdapterView.OnItemClickListener outputMenuItemListener = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			player.closeOutputMenu();
			changeOutput((SambaMedia.Output) parent.getItemAtPosition(position));
		}
	};

    private final AdapterView.OnItemClickListener captionMenuItemListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            player.closeCaptionMenu();
            changeCaption((SambaMedia.Caption) parent.getItemAtPosition(position));
        }
    };

	private final Runnable progressDispatcher = new Runnable() {
		@Override
		public void run() {
			if (player == null) return;
			
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PROGRESS, getCurrentTime(), getDuration()));
		}
	};

	public SambaPlayerController(FrameLayout view) {
		this.view = view;
	}

	public void setMedia(SambaMedia media) {
		if (media == null)
			throw new IllegalArgumentException("Media data is null");

		this.media = new SambaMediaConfig(media);

		destroy();

		// TODO: thumbnail
	}

	public SambaMedia getMedia() {
		return media;
	}

	public void play() {
		if (player != null)
			player.play();
		else create();
	}

	public void pause() {
		if (_hasStarted)
			player.pause();
	}

	public void stop() {
		player.stop();
		SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.STOP));
	}

	public void seek(float position) {
		player.seek(Math.round(position * 1000f));
	}

	public void setFullscreen(boolean flag) {
		player.setFullscreen(flag);
	}

	public boolean isFullscreen() {
		return player.isFullscreen();
	}

	public void setAutoFullscreenMode(boolean flag) {
		autoFsMode = flag;
	}

	public void setEnableControls(boolean flag) {
		if(media.isAudioOnly) return;

		if(player != null) {
			if(flag)
				player.enableControls();
			else
				player.disableControls();
		}
		else enableControls = flag;
	}

	public void show() {
		player.show();
	}

	public void hide() {
		player.hide();
	}

	public float getCurrentTime() {
		return player.getCurrentPosition()/1000f;
	}

	public float getDuration() {
		return player.getDuration()/1000f;
	}

	public boolean hasStarted() {
		return _hasStarted;
	}

	public boolean hasFinished() {
		return _hasFinished;
	}

	public void destroy() {
		destroyInternal();
		SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.UNLOAD));
	}

	public void changeOutput(SambaMedia.Output output) {
		int currentPosition = player.getCurrentPosition();
		for(SambaMedia.Output o : media.outputs) {
			o.current = o.label.equals(output.label);
		}
		media.url = output.url;
		destroyInternal();
		create(false);
		player.seek(currentPosition);
	}

    //Captions
    public void changeCaption(SambaMedia.Caption caption) {
        for(SambaMedia.Caption c : media.captions ) {
            c.current = c.language.equals(caption.language);
        }
    }

	/**	End Player API **/

	private void create() {
		create(true);
	}

	private void create(boolean notify) {
		if (player != null) {
			Log.e("player", "Player already created!");
			return;
		}

        if (media.url == null || media.url.isEmpty()) {
			Log.e("player", "Media data is null!");
	        return;
		}

		Video.VideoType videoType = Video.VideoType.OTHER;

		switch (media.type.toLowerCase()) {
			case "hls":
				videoType = Video.VideoType.HLS;
				break;
			case "dash":
				videoType = Video.VideoType.DASH;
				break;
		}

		// no autoplay if there's ad because ImaWrapper takes control of the player
        player = new SimpleVideoPlayer((Activity) view.getContext(), view,
                new Video(media.url, videoType), media.title,
		        media.adUrl == null || media.adUrl.isEmpty(), media.isAudioOnly);

		player.setSeekbarColor(media.themeColor);


		// Move the content player's surface layer to the background so that the ad player's surface
		// layer can be overlaid on top of it during ad playback.
		player.moveSurfaceToBackground();

		//Live treatment
		if (media.isLive) {
			((Activity) view.getContext()).findViewById(R.id.time_container).setVisibility(View.INVISIBLE);

			player.setControlsVisible(false, "seekbar");
			player.addActionButton(ContextCompat.getDrawable(view.getContext(), R.drawable.ic_live),
					view.getContext().getString(R.string.live), null);
		}

		/**player.addActionButton(ContextCompat.getDrawable(view.getContext(), R.drawable.share),
		        view.getContext().getString(R.string.share_facebook), new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Toast.makeText(view.getContext(), "Share Facebook", Toast.LENGTH_SHORT).show();
			}
		});**/

		player.addPlaybackListener(playbackListener);
		player.setPlayCallback(playListener);

		if (media.isAudioOnly) {
			player.setControlsVisible(true, "play");
			player.setControlsVisible(false, "fullscreen", "playLarge", "topChrome");
			//playbackControlLayer.swapControls("time", "seekbar");
			player.setBackgroundColor(0xFF434343);
			player.setChromeColor(0x00000000);
		}
		else player.setFullscreenCallback(fullscreenListener);

		// Fullscreen
		orientationEventListener = new OrientationEventListener(view.getContext()) {

			{ enable(); }

			@Override
			public void onOrientationChanged( int orientation) {
				if (Settings.System.getInt(view.getContext().getContentResolver(),
						Settings.System.ACCELEROMETER_ROTATION, 0) == 0 || !autoFsMode || player == null)
					return;

				if(orientation <= 15 && orientation >= 0) {
					if(player.isFullscreen()) {
						player.setFullscreen(false);
					}
					SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.PORTRAIT));
				}else if((orientation >= 80 && orientation <= 100 ) || (orientation >= 260 && orientation <= 290)){
					if(!player.isFullscreen()) {
						player.setFullscreen(true);
					}
					SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.LANDSCAPE));
				}
			}
		};

		if (notify) {
			SambaEventBus.post(new SambaEvent(SambaPlayerListener.EventType.LOAD, view));
		}

		// Output Menu
		// TODO it might not be here
		if (media.outputs != null && media.outputs.size() > 1 && !media.isAudioOnly) {
			outputMenu = ((Activity) view.getContext()).getLayoutInflater().inflate(R.layout.menu_layout, null);

			TextView cancelButton = (TextView)outputMenu.findViewById(R.id.menu_cancel_button);
			//cancelButton.setTextColor(media.themeColor);

			cancelButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					player.closeOutputMenu();
				}
			});

			OutputAdapter outputAdapter = new OutputAdapter(view.getContext(), media.outputs);
			ListView outputMenuList = (ListView) outputMenu.findViewById(R.id.menu_list);

			outputMenuList.setAdapter(outputAdapter);
			outputMenuList.setOnItemClickListener(outputMenuItemListener);
			outputAdapter.notifyDataSetChanged();
			player.setOutputMenu(outputMenu);
		}

        //Captions
        if(media.captions != null && media.captions.size() > 0 ) {
            captionMenu = ((Activity) view.getContext()).getLayoutInflater().inflate(R.layout.menu_layout, null);
            TextView captionCancelButton = (TextView) captionMenu.findViewById(R.id.menu_cancel_button);
            TextView captionTitle = (TextView)captionMenu.findViewById(R.id.menu_label);
            captionTitle.setText(view.getContext().getString(R.string.captions));

            captionCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    player.closeCaptionMenu();
                }
            });

            CaptionAdapter captionAdapter = new CaptionAdapter(view.getContext(), media.captions);
            ListView captionMenuList = (ListView) captionMenu.findViewById(R.id.menu_list);

            captionMenuList.setAdapter(captionAdapter);
            captionMenuList.setOnItemClickListener(captionMenuItemListener);
            captionMenuList.deferNotifyDataSetChanged();

            player.setCaptionMenu(captionMenu);
        }


		if(!enableControls && !media.isAudioOnly) {
			player.disableControls();
		}
	}

	private void destroyInternal() {
		if (player == null)
			return;

		stopProgressTimer();
		stop();

		if (outputMenu != null) {
			((ListView)outputMenu.findViewById(R.id.menu_list)).setOnItemClickListener(null);
			outputMenu.findViewById(R.id.menu_cancel_button).setOnClickListener(null);
		}

        if(captionMenu != null) {
            ((ListView)captionMenu.findViewById(R.id.menu_list)).setOnItemClickListener(null);
            captionMenu.findViewById(R.id.menu_cancel_button).setOnClickListener(null);
        }

		orientationEventListener.disable();
		player.setPlayCallback(null);
		player.setFullscreenCallback(null);
		player.release();

		outputMenu = null;
		orientationEventListener = null;
		player = null;
		_hasStarted = false;
		_hasFinished = false;
	}

    private void startProgressTimer() {
		if (progressTimer != null)
			return;

		progressTimer = new Timer();
		progressTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				((Activity)view.getContext()).runOnUiThread(progressDispatcher);
			}
		}, 0, 250);
	}

	private void stopProgressTimer() {
		if (progressTimer == null)
			return;

		progressTimer.cancel();
		progressTimer.purge();
		progressTimer = null;
	}
}
