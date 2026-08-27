// AirPlayReceiver.exe — native Windows launcher for the bundled-jre airplay stack.
// Replaces run_airplay_gui.bat: sets PATH/GST_PLUGIN_PATH, starts javaw with the Swing
// launcher jar hidden from console. Compiled with .NET Framework 4 csc, no deps.
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace AirPlayLauncher
{
    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            // walk up from the exe location until we find a dir that has both
            // jre\bin\javaw.exe and the launcher jar (exe may sit in packaging\)
            string appDir = FindAppDir(AppDomain.CurrentDomain.BaseDirectory);
            if (appDir == null)
            {
                MessageBox.Show(
                    "未找到 Java 运行环境。\n请将本程序与 jre 文件夹放在同一目录下。\n\n" +
                    "Bundled Java runtime was not found next to this exe, nor in any parent folder.",
                    "AirPlay 接收端 / AirPlay Receiver",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }

            string javaExe = Path.Combine(appDir, "jre", "bin", "javaw.exe");
            string serverJar = Path.Combine(appDir, "java-airplay-server-fixed.jar");
            string launcherJar = Path.Combine(appDir, "java-airplay-launcher.jar");

            if (!File.Exists(javaExe))
            {
                MessageBox.Show(
                    "未找到 Java 运行环境：\n" + javaExe +
                    "\n\nBundled Java runtime was not found.",
                    "AirPlay 接收端 / AirPlay Receiver",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }

            bool useLauncherJar = File.Exists(launcherJar);
            string jar = useLauncherJar ? launcherJar : serverJar;
            if (!File.Exists(jar))
            {
                MessageBox.Show(
                    "未找到主程序 JAR：\n" + jar +
                    "\n请确认程序目录完整。\n\nRequired jar was not found.",
                    "AirPlay 接收端 / AirPlay Receiver",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }

            var psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = "-Dfile.encoding=UTF-8 -jar \"" + jar + "\" --base-dir=\"" + appDir + "\"",
                UseShellExecute = false,
                WorkingDirectory = appDir,
            };

            // prepend bundled runtime dirs so Netty/GStreamer natives resolve
            string path = Environment.GetEnvironmentVariable("PATH") ?? "";
            string gstBin = Path.Combine(appDir, "gstreamer", "bin");
            string gstPlugins = Path.Combine(appDir, "gstreamer", "lib", "gstreamer-1.0");
            psi.EnvironmentVariables["PATH"] =
                Path.Combine(appDir, "jre", "bin") + ";" +
                (Directory.Exists(gstBin) ? gstBin + ";" : "") + path;
            if (Directory.Exists(gstPlugins))
                psi.EnvironmentVariables["GST_PLUGIN_PATH"] = gstPlugins;

            try
            {
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show("启动失败 / Failed to start:\n" + ex.Message,
                    "AirPlay 接收端 / AirPlay Receiver",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }
            return 0;
        }

        static string FindAppDir(string start)
        {
            DirectoryInfo dir = new DirectoryInfo(start);
            for (int i = 0; i < 4 && dir != null; i++, dir = dir.Parent)
            {
                if (File.Exists(Path.Combine(dir.FullName, "jre", "bin", "javaw.exe")) &&
                    (File.Exists(Path.Combine(dir.FullName, "java-airplay-launcher.jar")) ||
                     File.Exists(Path.Combine(dir.FullName, "java-airplay-server-fixed.jar"))))
                    return dir.FullName;
            }
            return null;
        }
    }
}
