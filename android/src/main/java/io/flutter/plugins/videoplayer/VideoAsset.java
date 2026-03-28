// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.source.MediaSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A video to be played by {@link VideoPlayer}. */
public abstract class VideoAsset {
  /**
   * Returns an asset from a local {@code asset:///} URL, i.e. an on-device asset.
   *
   * @param assetUrl local asset, beginning in {@code asset:///}.
   * @return the asset.
   */
  @NonNull
  static VideoAsset fromAssetUrl(@NonNull String assetUrl) {
    if (!assetUrl.startsWith("asset:///")) {
      throw new IllegalArgumentException("assetUrl must start with 'asset:///'");
    }
    return new LocalVideoAsset(assetUrl);
  }

  /**
   * Returns an asset from a remote URL.
   *
   * @param remoteUrl remote asset, i.e. typically beginning with {@code https://} or similar.
   * @param streamingFormat which streaming format, provided as a hint if able.
   * @param httpHeaders HTTP headers to set for a request.
   * @return the asset.
   */
  @NonNull
  static VideoAsset fromRemoteUrl(
      @Nullable String remoteUrl,
      @NonNull StreamingFormat streamingFormat,
      @NonNull Map<String, String> httpHeaders,
      @Nullable String userAgent) {
    return new HttpVideoAsset(remoteUrl, streamingFormat, new HashMap<>(httpHeaders), userAgent);
  }

  /**
   * Returns an asset from a RTSP URL.
   *
   * @param rtspUrl remote asset, beginning with {@code rtsp://}.
   * @return the asset.
   */
  @NonNull
  static VideoAsset fromRtspUrl(@NonNull String rtspUrl) {
    if (!rtspUrl.startsWith("rtsp://")) {
      throw new IllegalArgumentException("rtspUrl must start with 'rtsp://'");
    }
    return new RtspVideoAsset(rtspUrl);
  }

  @Nullable protected final String assetUrl;

  protected VideoAsset(@Nullable String assetUrl) {
    this.assetUrl = assetUrl;
  }

  static final class DurationResolverOptions {
    private static final String USER_AGENT_HEADER = "User-Agent";

    @NonNull final Map<String, String> httpHeaders;
    final boolean allowMetadataRetriever;

    private DurationResolverOptions(
        @NonNull Map<String, String> httpHeaders, boolean allowMetadataRetriever) {
      this.httpHeaders = httpHeaders;
      this.allowMetadataRetriever = allowMetadataRetriever;
    }

    @NonNull
    static DurationResolverOptions forLocalPlayback() {
      return new DurationResolverOptions(Collections.emptyMap(), true);
    }

    @NonNull
    static DurationResolverOptions forRemotePlayback(
        @NonNull StreamingFormat streamingFormat,
        @NonNull Map<String, String> httpHeaders,
        @Nullable String userAgent) {
      if (streamingFormat != StreamingFormat.UNKNOWN) {
        return new DurationResolverOptions(Collections.emptyMap(), false);
      }

      Map<String, String> metadataHeaders = new HashMap<>(httpHeaders);
      if (userAgent != null
          && !userAgent.isEmpty()
          && !containsHeader(metadataHeaders, USER_AGENT_HEADER)) {
        metadataHeaders.put(USER_AGENT_HEADER, userAgent);
      }
      return new DurationResolverOptions(
          Collections.unmodifiableMap(metadataHeaders), /* allowMetadataRetriever= */ true);
    }

    private static boolean containsHeader(
        @NonNull Map<String, String> headers, @NonNull String headerName) {
      for (String key : headers.keySet()) {
        if (key.equalsIgnoreCase(headerName)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Returns the configured media item to be played.
   *
   * @return media item.
   */
  @NonNull
  public abstract MediaItem getMediaItem();

  /**
   * Returns the configured media source factory, if needed for this asset type.
   *
   * @param context application context.
   * @return configured factory, or {@code null} if not needed for this asset type.
   */
  @NonNull
  public abstract MediaSource.Factory getMediaSourceFactory(@NonNull Context context);

  /** Streaming formats that can be provided to the video player as a hint. */
  enum StreamingFormat {
    /** Default, if the format is either not known or not another valid format. */
    UNKNOWN,

    /** Smooth Streaming. */
    SMOOTH,

    /** MPEG-DASH (Dynamic Adaptive over HTTP). */
    DYNAMIC_ADAPTIVE,

    /** HTTP Live Streaming (HLS). */
    HTTP_LIVE
  }
}
