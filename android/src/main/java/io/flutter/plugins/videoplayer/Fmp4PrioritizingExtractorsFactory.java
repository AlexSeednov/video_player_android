// Copyright 2013 The Flutter Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.videoplayer;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorsFactory;
import java.util.List;
import java.util.Map;

/**
 * An {@link ExtractorsFactory} that wraps {@link DefaultExtractorsFactory} but always uses the
 * fixed extractor order, ensuring {@code FragmentedMp4Extractor} is tried before {@code
 * Mp4Extractor}.
 *
 * <p>By default, {@link DefaultExtractorsFactory#createExtractors(Uri, Map)} reorders extractors
 * based on the URI file extension. For {@code .mp4} URIs this places {@code Mp4Extractor} first,
 * which incorrectly parses fragmented MP4 files and reports only the init segment duration from
 * the {@code mvhd} atom instead of the full video duration. The fixed order from {@link
 * DefaultExtractorsFactory#createExtractors()} places {@code FragmentedMp4Extractor} before {@code
 * Mp4Extractor}, allowing proper fmp4 detection via the {@code moof}/{@code mvex} atoms and correct
 * duration resolution from {@code mehd} or {@code sidx} metadata.
 */
@OptIn(markerClass = UnstableApi.class)
final class Fmp4PrioritizingExtractorsFactory implements ExtractorsFactory {
  private final DefaultExtractorsFactory defaultFactory = new DefaultExtractorsFactory();

  @NonNull
  @Override
  public Extractor[] createExtractors() {
    return defaultFactory.createExtractors();
  }

  @NonNull
  @Override
  public Extractor[] createExtractors(
      @NonNull Uri uri, @NonNull Map<String, List<String>> responseHeaders) {
    // Bypass URI-based extractor reordering. For .mp4 URIs the default implementation
    // prioritizes Mp4Extractor, which misreads fmp4 duration from the mvhd atom.
    // The fixed order always places FragmentedMp4Extractor before Mp4Extractor.
    return defaultFactory.createExtractors();
  }
}
