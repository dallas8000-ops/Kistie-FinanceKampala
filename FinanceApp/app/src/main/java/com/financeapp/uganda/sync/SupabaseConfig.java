package com.financeapp.uganda.sync;

import com.financeapp.uganda.BuildConfig;
import java.net.URI;

public final class SupabaseConfig {
    private final String projectUrl;
    private final String publishableKey;

    public SupabaseConfig(String projectUrl, String publishableKey) {
        this.projectUrl = projectUrl == null ? "" : projectUrl.trim();
        this.publishableKey = publishableKey == null ? "" : publishableKey.trim();
    }

    public static SupabaseConfig fromBuildConfig() {
        return new SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY);
    }

    public boolean isConfigured() {
        if (projectUrl.isEmpty() || publishableKey.isEmpty()) return false;
        try {
            URI uri = URI.create(projectUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
        String normalizedKey = publishableKey.toLowerCase(java.util.Locale.ROOT);
        return !normalizedKey.contains("service_role") && !normalizedKey.startsWith("sb_secret_");
    }

    public String getProjectUrl() { return projectUrl; }
    public String getPublishableKey() { return publishableKey; }
}
