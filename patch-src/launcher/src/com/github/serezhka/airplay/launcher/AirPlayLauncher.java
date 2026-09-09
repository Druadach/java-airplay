package com.github.serezhka.airplay.launcher;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class AirPlayLauncher {
    private static volatile UiLanguage currentLanguage = UiLanguage.systemDefault();

    private AirPlayLauncher() {
    }

    static void setCurrentLanguage(UiLanguage language) {
        currentLanguage = language;
    }

    public static void main(String[] arguments) {
        if (hasArgument(arguments, "--validate")) {
            validateInstallation(arguments);
            return;
        }

        boolean minimized = hasArgument(arguments, "--minimized");
        boolean automaticLaunch = hasArgument(arguments, "--auto-start");
        boolean updateRelaunch = hasArgument(arguments, "--updated-relaunch");
        boolean resumeService = hasArgument(arguments, "--resume-service");
        boolean updateHandshake = hasArgument(arguments, "--update-job");

        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                        null,
                        LauncherMessages.failureText(currentLanguage, failure),
                        LauncherMessages.text(
                                currentLanguage, LauncherMessages.Key.DIALOG_LAUNCHER_ERROR_TITLE),
                        JOptionPane.ERROR_MESSAGE)));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                Path baseDirectory = resolveBaseDirectory(arguments);
                currentLanguage = new ConfigStore(
                        baseDirectory.resolve("application.properties")).loadLanguage();
                UpdateInstaller.Startup startup = UpdateInstaller.startup(baseDirectory, arguments);
                LauncherFrame frame = new LauncherFrame(baseDirectory, startup != null);
                Runtime.getRuntime().addShutdownHook(
                        new Thread(frame::shutdownFromHook, "airplay-launcher-shutdown"));

                if (startup == null) {
                    frame.showAtStartup(minimized, automaticLaunch);
                    frame.startServiceAtStartup(updateRelaunch, resumeService);
                } else {
                    CompletableFuture.runAsync(() -> {
                        try {
                            UpdateInstaller.acknowledgeStartup(startup);
                        } catch (IOException exception) {
                            throw new CompletionException(exception);
                        }
                    }).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
                        if (failure != null) {
                            System.err.println(failure.getMessage());
                            System.exit(1);
                            return;
                        }
                        frame.completeUpdateStartup();
                        frame.showAtStartup(minimized, automaticLaunch);
                        frame.startServiceAtStartup(updateRelaunch, resumeService);
                    }));
                }
            } catch (Exception exception) {
                if (updateHandshake) {
                    System.err.println(exception.getMessage());
                    System.exit(1);
                    return;
                }
                JOptionPane.showMessageDialog(
                        null,
                        LauncherMessages.failureText(currentLanguage, exception),
                        LauncherMessages.text(currentLanguage,
                                LauncherMessages.Key.DIALOG_LAUNCHER_START_ERROR_TITLE),
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void validateInstallation(String[] arguments) {
        try {
            Path baseDirectory = resolveBaseDirectory(arguments);
            new ConfigStore(baseDirectory.resolve("application.properties")).load();
            AppVersion.current();
            UpdateInstaller.validateResources();
            if (AirPlayLauncher.class.getResource("/menu/tray_icon.png") == null) {
                throw new LauncherIOException(LauncherMessages.Key.VALIDATION_ICON_MISSING);
            }
            System.out.println("Java AirPlay launcher validation passed: " + baseDirectory);
        } catch (Exception exception) {
            System.err.println("Java AirPlay launcher validation failed: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static boolean hasArgument(String[] arguments, String expected) {
        for (String argument : arguments) {
            if (expected.equals(argument)) {
                return true;
            }
        }
        return false;
    }

    static Path resolveBaseDirectory(String[] arguments) throws IOException, URISyntaxException {
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument.equals("--base-dir")) {
                if (++index >= arguments.length || arguments[index].isBlank()
                        || arguments[index].startsWith("--")) {
                    throw new LauncherIOException(
                            LauncherMessages.Key.VALIDATION_INVALID_INSTALLATION, "--base-dir");
                }
                Path explicit = Path.of(arguments[index]).toAbsolutePath().normalize();
                requireInstallation(explicit);
                return explicit;
            }
            if (argument.startsWith("--base-dir=")) {
                Path explicit = Path.of(argument.substring("--base-dir=".length())).toAbsolutePath().normalize();
                requireInstallation(explicit);
                return explicit;
            }
        }

        Path codeLocation = Path.of(AirPlayLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
        Path discovered = findInstallation(Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent());
        if (discovered != null) {
            return discovered;
        }

        Path current = Path.of("").toAbsolutePath().normalize();
        discovered = findInstallation(current);
        if (discovered != null) {
            return discovered;
        }
        throw new LauncherIOException(LauncherMessages.Key.VALIDATION_INSTALLATION_NOT_FOUND);
    }

    private static Path findInstallation(Path start) {
        Path candidate = start;
        for (int level = 0; candidate != null && level < 8; level++) {
            if (isInstallation(candidate)) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }

    private static void requireInstallation(Path directory) throws IOException {
        if (!isInstallation(directory)) {
            throw new LauncherIOException(
                    LauncherMessages.Key.VALIDATION_INVALID_INSTALLATION, directory);
        }
    }

    private static boolean isInstallation(Path directory) {
        return Files.isRegularFile(directory.resolve("java-airplay-server-fixed.jar"))
                && Files.isRegularFile(directory.resolve("jre/bin/javaw.exe"));
    }
}
