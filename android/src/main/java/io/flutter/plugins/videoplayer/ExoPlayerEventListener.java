// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;

public abstract class ExoPlayerEventListener implements Player.Listener {
  private static final String TAG = "ExoPlayerEventListener";
  private boolean isInitialized = false;
  private boolean isPlayingSuppressed = false;
  @NonNull protected final Context context;
  protected final ExoPlayer exoPlayer;
  protected final VideoPlayerCallbacks events;

  protected enum RotationDegrees {
    ROTATE_0(0),
    ROTATE_90(90),
    ROTATE_180(180),
    ROTATE_270(270);

    private final int degrees;

    RotationDegrees(int degrees) {
      this.degrees = degrees;
    }

    public static RotationDegrees fromDegrees(int degrees) {
      for (RotationDegrees rotationDegrees : RotationDegrees.values()) {
        if (rotationDegrees.degrees == degrees) {
          return rotationDegrees;
        }
      }
      throw new IllegalArgumentException("Invalid rotation degrees specified: " + degrees);
    }

    public int getDegrees() {
      return this.degrees;
    }
  }

  public ExoPlayerEventListener(
      @NonNull Context context,
      @NonNull ExoPlayer exoPlayer,
      @NonNull VideoPlayerCallbacks events) {
    this.context = context;
    this.exoPlayer = exoPlayer;
    this.events = events;
  }

  protected abstract void sendInitialized();

  /**
   * Returns the most accurate duration available for the current media, in milliseconds.
   *
   * <p>For fragmented MP4 files that lack a {@code mehd} box, ExoPlayer may report only the
   * duration from the {@code mvhd} atom, which corresponds to the init segment rather than the
   * full video. This method uses {@link MediaMetadataRetriever} as a fallback for local playback
   * and progressive HTTP(S) playback. Known streaming protocols such as HLS, DASH, and Smooth
   * Streaming skip the extra metadata pass.
   */
  @SuppressWarnings("deprecation") // MediaMetadataRetriever.release() deprecated in API 29
  protected long resolveAccurateDuration() {
    long exoPlayerDuration = exoPlayer.getDuration();

    MediaItem mediaItem = exoPlayer.getCurrentMediaItem();
    if (mediaItem == null || mediaItem.localConfiguration == null) {
      return exoPlayerDuration;
    }

    VideoAsset.DurationResolverOptions resolverOptions = null;
    Object tag = mediaItem.localConfiguration.tag;
    if (tag instanceof VideoAsset.DurationResolverOptions) {
      resolverOptions = (VideoAsset.DurationResolverOptions) tag;
      if (!resolverOptions.allowMetadataRetriever) {
        return exoPlayerDuration;
      }
    }

    Uri uri = mediaItem.localConfiguration.uri;
    String scheme = uri.getScheme();
    if (scheme == null) {
      return exoPlayerDuration;
    }

    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      switch (scheme) {
        case "file":
          retriever.setDataSource(uri.getPath());
          break;
        case "content":
          retriever.setDataSource(context, uri);
          break;
        case "asset":
          String assetPath = uri.getPath();
          if (assetPath == null || assetPath.isEmpty()) {
            return exoPlayerDuration;
          }
          if (assetPath != null && assetPath.startsWith("/")) {
            assetPath = assetPath.substring(1);
          }
          try (AssetFileDescriptor afd = context.getAssets().openFd(assetPath)) {
            retriever.setDataSource(
                afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
          }
          break;
        case "http":
        case "https":
          if (resolverOptions == null) {
            return exoPlayerDuration;
          }
          retriever.setDataSource(uri.toString(), resolverOptions.httpHeaders);
          break;
        default:
          return exoPlayerDuration;
      }

      String durationStr =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (durationStr != null) {
        long retrievedDuration = Long.parseLong(durationStr);
        if (retrievedDuration > 0) {
          return retrievedDuration;
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Falling back to ExoPlayer duration for " + uri, e);
    } finally {
      try {
        retriever.release();
      } catch (Exception e) {
        // Ignore release errors.
      }
    }

    return exoPlayerDuration;
  }

  @Override
  public void onPlaybackStateChanged(final int playbackState) {
    PlatformPlaybackState platformState = PlatformPlaybackState.UNKNOWN;
    switch (playbackState) {
      case Player.STATE_BUFFERING:
        platformState = PlatformPlaybackState.BUFFERING;
        break;
      case Player.STATE_READY:
        platformState = PlatformPlaybackState.READY;
        if (!isInitialized) {
          isInitialized = true;
          sendInitialized();
        }
        break;
      case Player.STATE_ENDED:
        platformState = PlatformPlaybackState.ENDED;
        break;
      case Player.STATE_IDLE:
        platformState = PlatformPlaybackState.IDLE;
        break;
    }
    events.onPlaybackStateChanged(platformState);
  }

  @Override
  public void onPlayerError(@NonNull final PlaybackException error) {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
      // See
      // https://exoplayer.dev/live-streaming.html#behindlivewindowexception-and-error_code_behind_live_window
      exoPlayer.seekToDefaultPosition();
      exoPlayer.prepare();
    } else {
      events.onError("VideoError", "Video player had error " + error, null);
    }
  }

  @Override
  public void onIsPlayingChanged(boolean isPlaying) {
    // During seek (or buffering) while playing, ExoPlayer temporarily reports
    // isPlaying=false even though playWhenReady remains true. Suppress that
    // transient event so the Dart side doesn't see a spurious pause/resume.
    if (!isPlaying && exoPlayer.getPlayWhenReady()) {
      isPlayingSuppressed = true;
      return;
    }
    isPlayingSuppressed = false;
    events.onIsPlayingStateUpdate(isPlaying);
  }

  @Override
  public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
    if (isPlayingSuppressed && !playWhenReady) {
      // User intentionally paused while we were suppressing isPlaying events.
      isPlayingSuppressed = false;
      events.onIsPlayingStateUpdate(false);
    }
  }

  @Override
  public void onTracksChanged(@NonNull Tracks tracks) {
    // Find the currently selected audio track and notify
    String selectedTrackId = findSelectedAudioTrackId(tracks);
    events.onAudioTrackChanged(selectedTrackId);
  }

  /**
   * Finds the ID of the currently selected audio track.
   *
   * @param tracks The current tracks
   * @return The track ID in format "groupIndex_trackIndex", or null if no audio track is selected
   */
  @Nullable
  private String findSelectedAudioTrackId(@NonNull Tracks tracks) {
    int groupIndex = 0;
    for (Tracks.Group group : tracks.getGroups()) {
      if (group.getType() == C.TRACK_TYPE_AUDIO && group.isSelected()) {
        // Find the selected track within this group
        for (int i = 0; i < group.length; i++) {
          if (group.isTrackSelected(i)) {
            return groupIndex + "_" + i;
          }
        }
      }
      groupIndex++;
    }
    return null;
  }
}
