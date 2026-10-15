package com.financeapp.uganda.sync;

import com.financeapp.uganda.BuildConfig;
import java.net.URI;

public final class SupabaseConfig {
    private final String projectUrl;
    private final String anonKey;

    public SupabaseConfig(String projectUrl, String anonKey) {
        this.projectUrl = projectUrl == null ? "" : projectUrl.trim();
        this.anonKey = anonKey == null ? "" : anonKey.trim();
    }

    public static SupabaseConfig fromBuildConfig() {
        return new SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY);
    }

    public boolean isConfigured() {
        if (projectUrl.isEmpty() || anonKey.isEmpty()) return false;
        try {
            URI uri = URI.create(projectUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) return false;
        } catch (IllegalArgumentException e) {
            return false;
        }
        String normalizedKey = anonKey.toLowerCase(java.util.Locale.ROOT);
        return !normalizedKey.contains("service_role") && !normalizedKey.startsWith("sb_secret_");
    }

    public String getProjectUrl() { return projectUrl; }
    public String getAnonKey() { return anonKey; }
}
