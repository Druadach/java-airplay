package com.github.serezhka.airplay.launcher;

import java.net.URI;
import java.util.List;

record UpdateAsset(String name, long size, String sha256, URI downloadUri) {
    static final long MAX_DOWNLOAD_BYTES = 1_073_741_824L;

    static UpdateAsset select(String tagName, List<GitHubRelease.Asset> assets) {
        String version = tagName.replaceFirst("^[vV]", "");
        String expectedName = "AirPlayReceiver_Portable_" + version + ".zip";
        URI expectedUri = URI.create(GitHubUpdateChecker.RELEASES_PAGE
                + "/download/" + tagName + "/" + expectedName);
        UpdateAsset selected = null;
        for (GitHubRelease.Asset asset : assets) {
            if (!expectedName.equals(asset.name()) || !"uploaded".equals(asset.state())
                    || asset.size() <= 0 || asset.size() > MAX_DOWNLOAD_BYTES
                    || asset.digest() == null || !asset.digest().matches("sha256:[0-9a-fA-F]{64}")
                    || !expectedUri.toString().equals(asset.downloadUrl())) {
                continue;
            }
            if (selected != null) {
                return null;
            }
            selected = new UpdateAsset(expectedName, asset.size(), asset.digest().substring(7), expectedUri);
        }
        return selected;
    }
}
