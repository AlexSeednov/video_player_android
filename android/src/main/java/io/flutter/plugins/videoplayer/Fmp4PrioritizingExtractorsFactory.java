// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import java.util.List;
import java.util.Map;

/**
 * An {@link ExtractorsFactory} that exclusively uses {@link FragmentedMp4Extractor}.
 *
 * <p>This is a diagnostic factory to verify that fmp4 parsing with {@link FragmentedMp4Extractor}
 * produces the correct duration. All content is forced through this single extractor.
 */
@OptIn(markerClass = UnstableApi.class)
final class Fmp4PrioritizingExtractorsFactory implements ExtractorsFactory {

  @NonNull
  @Override
  public Extractor[] createExtractors() {
    return new Extractor[] {new FragmentedMp4Extractor()};
  }

  @NonNull
  @Override
  public Extractor[] createExtractors(
      @NonNull Uri uri, @NonNull Map<String, List<String>> responseHeaders) {
    return new Extractor[] {new FragmentedMp4Extractor()};
  }
}
