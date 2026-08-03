package com.UIN.Tool.app;

import android.app.Application;
import android.content.Context;

import com.UIN.Tool.BuildConfig;
import com.UIN.Tool.shared.errors.Error;
import com.UIN.Tool.shared.logger.Logger;
import com.UIN.Tool.shared.termux.TermuxBootstrap;
import com.UIN.Tool.shared.termux.TermuxConstants;
import com.UIN.Tool.shared.termux.crash.TermuxCrashUtils;
import com.UIN.Tool.shared.termux.file.TermuxFileUtils;
import com.UIN.Tool.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.UIN.Tool.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.UIN.Tool.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.UIN.Tool.shared.termux.shell.am.TermuxAmSocketServer;
import com.UIN.Tool.shared.termux.shell.TermuxShellManager;
import com.UIN.Tool.shared.termux.theme.TermuxThemeUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class TermuxApplication extends Application {

    private static final String LOG_TAG = "TermuxApplication";

    public void onCreate() {
        super.onCreate();

        Context context = getApplicationContext();

        // Set crash handler for the app
        TermuxCrashUtils.setDefaultCrashHandler(this);

        // Set log config for the app
        setLogConfig(context);

        Logger.logDebug("Starting Application");

        // Set TermuxBootstrap.TERMUX_APP_PACKAGE_MANAGER and TermuxBootstrap.TERMUX_APP_PACKAGE_VARIANT
        TermuxBootstrap.setTermuxPackageManagerAndVariant(BuildConfig.TERMUX_PACKAGE_VARIANT);

        // Must run before TermuxAppSharedProperties.init() below, which loads and caches the
        // properties file; otherwise the in-process cache would keep allow-external-apps=false.
        ensureTermuxPropertiesFile();

        // Init app wide SharedProperties loaded from termux.properties
        TermuxAppSharedProperties properties = TermuxAppSharedProperties.init(context);

        // Init app wide shell manager
        TermuxShellManager shellManager = TermuxShellManager.init(context);

        // Set NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.getNightMode());

        // Check and create termux files directory. If failed to access it like in case of secondary
        // user or external sd card installation, then don't run files directory related code
        Error error = TermuxFileUtils.isTermuxFilesDirectoryAccessible(this, true, true);
        boolean isTermuxFilesDirectoryAccessible = error == null;
        if (isTermuxFilesDirectoryAccessible) {
            Logger.logInfo(LOG_TAG, "Termux files directory is accessible");

            error = TermuxFileUtils.isAppsTermuxAppDirectoryAccessible(true, true);
            if (error != null) {
                Logger.logErrorExtended(LOG_TAG, "Create apps/termux-app directory failed\n" + error);
                return;
            }

            // Setup termux-am-socket server
            TermuxAmSocketServer.setupTermuxAmSocketServer(context);
        } else {
            Logger.logErrorExtended(LOG_TAG, "Termux files directory is not accessible\n" + error);
        }

        // Init TermuxShellEnvironment constants and caches after everything has been setup including termux-am-socket server
        TermuxShellEnvironment.init(this);

        if (isTermuxFilesDirectoryAccessible) {
            TermuxShellEnvironment.writeEnvironmentToFile(this);
        }
    }

    public static void setLogConfig(Context context) {
        Logger.setDefaultLogTag(TermuxConstants.TERMUX_APP_NAME);

        // Load the log level from shared preferences and set it to the {@link Logger.CURRENT_LOG_LEVEL}
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context);
        if (preferences == null) return;
        preferences.setLogLevel(null, preferences.getLogLevel());
    }

    /**
     * 确保 termux.properties 存在且包含 allow-external-apps=true。
     *
     * RunCommandService（供插件后端 pre-command 使用）会检查该属性，未设为 true 时会拒绝执行并报错
     * "RunCommandService requires 'allow-external-apps' property to be set to 'true'"。
     * 必须在 TermuxAppSharedProperties.init()（加载并缓存属性）之前调用，否则进程内的缓存值恒为 false。
     */
    private void ensureTermuxPropertiesFile() {
        try {
            File dir = new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);
            if (!dir.exists() && !dir.mkdirs()) {
                Logger.logError(LOG_TAG, "Failed to create termux home directory: " + dir.getAbsolutePath());
                return;
            }

            File propsFile = new File(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH);
            boolean hasKey = false;
            StringBuilder extra = new StringBuilder();

            if (propsFile.exists()) {
                List<String> lines = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(propsFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                        if (line.trim().startsWith(TermuxConstants.PROP_ALLOW_EXTERNAL_APPS + "=")) {
                            hasKey = true;
                        }
                    }
                }
                if (!hasKey) {
                    if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
                        extra.append('\n');
                    }
                    extra.append(TermuxConstants.PROP_ALLOW_EXTERNAL_APPS).append("=true\n");
                }
            } else {
                extra.append(TermuxConstants.PROP_ALLOW_EXTERNAL_APPS).append("=true\n");
            }

            if (!hasKey) {
                try (FileWriter writer = new FileWriter(propsFile, true)) {
                    writer.write(extra.toString());
                }
                Logger.logInfo(LOG_TAG, "termux.properties updated: " + TermuxConstants.PROP_ALLOW_EXTERNAL_APPS + "=true");
            }
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to write termux.properties: " + e.getMessage());
        }
    }
}
