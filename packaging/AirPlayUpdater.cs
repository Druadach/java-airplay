using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Windows.Forms;
using System.Xml;

namespace AirPlayLauncher.Update
{
    internal static class UpdatePaths
    {
        internal const string WorkDirectory = ".airplay-update";

        internal static bool Exists(string target)
        {
            try
            {
                File.GetAttributes(target);
                return true;
            }
            catch (FileNotFoundException) { return false; }
            catch (DirectoryNotFoundException) { return false; }
        }

        internal static void RequireOrdinaryPath(string target)
        {
            string current = Path.GetFullPath(target);
            while (!String.IsNullOrEmpty(current))
            {
                if (Exists(current) && (File.GetAttributes(current) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException("Update paths must not contain symbolic links or junctions: " + current);
                string parent = Path.GetDirectoryName(current);
                if (String.Equals(parent, current, StringComparison.OrdinalIgnoreCase))
                    break;
                current = parent;
            }
        }

        internal static void RequireTree(string root)
        {
            RequireOrdinaryPath(root);
            if (!Directory.Exists(root))
                return;
            var pending = new Stack<string>();
            pending.Push(root);
            while (pending.Count > 0)
            {
                foreach (string entry in Directory.EnumerateFileSystemEntries(pending.Pop()))
                {
                    FileAttributes attributes = File.GetAttributes(entry);
                    if ((attributes & FileAttributes.ReparsePoint) != 0)
                        throw new IOException("Update trees must not contain symbolic links or junctions: " + entry);
                    if ((attributes & FileAttributes.Directory) != 0)
                        pending.Push(entry);
                }
            }
        }

        internal static void RequireJob(string baseDirectory, string jobDirectory)
        {
            string job = Path.GetFullPath(jobDirectory).TrimEnd(Path.DirectorySeparatorChar);
            string expectedParent = Path.Combine(Path.GetFullPath(baseDirectory), WorkDirectory);
            if (!String.Equals(Path.GetDirectoryName(job), expectedParent, StringComparison.OrdinalIgnoreCase)
                || !IsIdentifier(Path.GetFileName(job)))
                throw new IOException("The update workspace is outside the installation directory.");
            RequireOrdinaryPath(job);
        }

        internal static bool IsIdentifier(string value)
        {
            return value != null && Regex.IsMatch(value,
                @"\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z");
        }

        internal static void Move(string source, string destination)
        {
            RequireOrdinaryPath(source);
            RequireOrdinaryPath(destination);
            if (Exists(destination))
                throw new IOException("Refusing to overwrite a transaction path: " + destination);
            if (Directory.Exists(source))
                Directory.Move(source, destination);
            else
                File.Move(source, destination);
        }

        internal static void WriteMarker(string target, string token)
        {
            RequireOrdinaryPath(target);
            string temporary = target + ".tmp";
            using (var stream = new FileStream(temporary, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                byte[] bytes = Encoding.UTF8.GetBytes(token);
                stream.Write(bytes, 0, bytes.Length);
                stream.Flush(true);
            }
            File.Move(temporary, target);
        }

        internal static bool MatchesMarker(string target, string token)
        {
            if (!Exists(target))
                return false;
            RequireOrdinaryPath(target);
            return new FileInfo(target).Length <= 64 && File.ReadAllText(target, Encoding.UTF8) == token;
        }
    }

    internal sealed class UpdateApplyException : IOException
    {
        internal readonly bool Restored;

        internal UpdateApplyException(Exception original, bool restored, string recoveryError)
            : base(original.Message + (String.IsNullOrEmpty(recoveryError) ? "" : "\nRollback: " + recoveryError), original)
        {
            Restored = restored;
        }
    }

    internal static class UpdateTransaction
    {
        internal static readonly string[] ManagedNames = {
            "jre", "gstreamer", "java-airplay-launcher.jar", "java-airplay-server-fixed.jar",
            "AirPlayReceiver.exe", "run_airplay_gui.bat", "run_airplay_server.bat"
        };

        internal static void Apply(string baseDirectory, string jobDirectory, Action validate,
            Action activate, Action beforeRollback, Action<string> log)
        {
            UpdatePaths.RequireJob(baseDirectory, jobDirectory);
            string payload = Path.Combine(jobDirectory, "payload");
            string backup = Path.Combine(jobDirectory, "backup");
            string failed = Path.Combine(jobDirectory, "failed");
            UpdatePaths.RequireTree(payload);
            if (UpdatePaths.Exists(backup) || UpdatePaths.Exists(failed))
                throw new IOException("This update transaction has already been used.");
            foreach (string name in ManagedNames)
            {
                string source = Path.Combine(payload, name);
                bool directory = name == "jre" || name == "gstreamer";
                if (directory ? !Directory.Exists(source) : !File.Exists(source))
                    throw new IOException("Required update payload is missing: " + name);
                UpdatePaths.RequireTree(Path.Combine(baseDirectory, name));
            }
            Directory.CreateDirectory(backup);
            var backedUp = new List<string>();
            var installed = new List<string>();
            try
            {
                foreach (string name in ManagedNames)
                {
                    string target = Path.Combine(baseDirectory, name);
                    if (UpdatePaths.Exists(target))
                    {
                        log("Backup " + name);
                        UpdatePaths.Move(target, Path.Combine(backup, name));
                        backedUp.Add(name);
                    }
                    log("Install " + name);
                    UpdatePaths.Move(Path.Combine(payload, name), target);
                    installed.Add(name);
                }
                validate();
                activate();
            }
            catch (Exception original)
            {
                string recoveryError = "";
                try { beforeRollback(); }
                catch (Exception stopFailure)
                {
                    throw new UpdateApplyException(original, false, stopFailure.Message);
                }
                try { Directory.CreateDirectory(failed); }
                catch (Exception prepareFailure)
                {
                    throw new UpdateApplyException(original, false, prepareFailure.Message);
                }
                for (int index = ManagedNames.Length - 1; index >= 0; index--)
                {
                    string name = ManagedNames[index];
                    string target = Path.Combine(baseDirectory, name);
                    try
                    {
                        if (installed.Contains(name))
                            UpdatePaths.Move(target, Path.Combine(failed, name));
                        if (backedUp.Contains(name))
                            UpdatePaths.Move(Path.Combine(backup, name), target);
                    }
                    catch (Exception restoreFailure)
                    {
                        recoveryError += name + ": " + restoreFailure.Message + "\n";
                    }
                }
                throw new UpdateApplyException(original, recoveryError.Length == 0, recoveryError);
            }
        }
    }

    internal static class UpdaterProgram
    {
        private static readonly object LogLock = new object();

        [STAThread]
        private static int Main()
        {
            UpdateJob job = null;
            FileStream gate = null;
            Process replacement = null;
            bool parentExited = false;
            try
            {
                job = UpdateJob.Load(AppDomain.CurrentDomain.BaseDirectory);
                UpdatePaths.RequireOrdinaryPath(Path.Combine(job.BaseDirectory, UpdatePaths.WorkDirectory, "update.lock"));
                gate = new FileStream(Path.Combine(job.BaseDirectory, UpdatePaths.WorkDirectory, "update.lock"),
                    FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
                using (Process parent = Process.GetProcessById(job.ParentPid))
                {
                    long started = (parent.StartTime.ToUniversalTime().Ticks -
                        new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc).Ticks) / TimeSpan.TicksPerMillisecond;
                    if (Math.Abs(started - job.ParentStarted) > 1000)
                        throw new IOException("The original launcher process identity has changed.");
                    UpdatePaths.WriteMarker(Path.Combine(job.Directory, "ready"), job.Token);
                    var waiting = Stopwatch.StartNew();
                    while (!parent.WaitForExit(100))
                    {
                        if (UpdatePaths.MatchesMarker(Path.Combine(job.Directory, "cancel"), job.Token))
                            return 0;
                        if (waiting.Elapsed > TimeSpan.FromMinutes(2))
                            throw new IOException("The original launcher did not exit in time.");
                    }
                }
                parentExited = true;
                if (UpdatePaths.MatchesMarker(Path.Combine(job.Directory, "cancel"), job.Token)
                    || !UpdatePaths.MatchesMarker(Path.Combine(job.Directory, "apply"), job.Token))
                    return 0;
                Log(job, "Original launcher exited; applying update.");
                UpdateJob active = job;
                UpdateTransaction.Apply(job.BaseDirectory, job.Directory,
                    delegate { ValidateInstallation(active); },
                    delegate {
                        replacement = Launch(active, true);
                        WaitForStartup(active, replacement);
                        UpdatePaths.WriteMarker(Path.Combine(active.Directory, "commit"), active.Token);
                    },
                    delegate { StopChild(replacement); },
                    delegate(string text) { Log(active, text); });
                Log(job, "Update committed. Previous runtime retained in the backup directory.");
                return 0;
            }
            catch (Exception failure)
            {
                bool restored = !(failure is UpdateApplyException) || ((UpdateApplyException)failure).Restored;
                if (job != null)
                {
                    Log(job, failure.ToString());
                    try { File.WriteAllText(Path.Combine(job.Directory, "error.txt"), failure.Message, new UTF8Encoding(false)); }
                    catch (IOException) { }
                    catch (UnauthorizedAccessException) { }
                }
                if (gate != null)
                {
                    gate.Dispose();
                    gate = null;
                }
                if (parentExited && job != null)
                {
                    string message = job.Chinese
                        ? (restored ? "更新失败，已保留或还原旧版本。" : "更新失败，未能完整还原旧版本。请勿删除备份目录。")
                        : (restored ? "The update failed. The previous version has been kept or restored."
                            : "The update failed and rollback was incomplete. Do not delete the backup directory.");
                    if (restored)
                    {
                        try { Launch(job, false); }
                        catch (Exception restartFailure) { Log(job, restartFailure.ToString()); }
                    }
                    MessageBox.Show(message + "\n\n" + failure.Message + "\n\n" + job.Directory,
                        "AirPlay Receiver", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
                return 1;
            }
            finally
            {
                if (gate != null)
                    gate.Dispose();
                if (replacement != null)
                    replacement.Dispose();
            }
        }

        internal static void ValidateInstallation(UpdateJob job)
        {
            ProcessStartInfo info = JavaStartInfo(job, false);
            info.Arguments += " --validate";
            info.RedirectStandardOutput = true;
            info.RedirectStandardError = true;
            using (Process validation = new Process { StartInfo = info })
            {
                validation.OutputDataReceived += delegate(object sender, DataReceivedEventArgs output) {
                    if (output.Data != null) Log(job, output.Data);
                };
                validation.ErrorDataReceived += delegate(object sender, DataReceivedEventArgs output) {
                    if (output.Data != null) Log(job, output.Data);
                };
                validation.Start();
                validation.BeginOutputReadLine();
                validation.BeginErrorReadLine();
                if (!validation.WaitForExit(60000))
                {
                    StopChild(validation);
                    throw new IOException("The new runtime validation timed out.");
                }
                validation.WaitForExit();
                if (validation.ExitCode != 0)
                    throw new IOException("The new runtime validation failed (exit " + validation.ExitCode + ").");
            }
        }

        internal static Process Launch(UpdateJob job, bool updated)
        {
            ProcessStartInfo info = JavaStartInfo(job, true);
            info.Arguments += " --updated-relaunch";
            if (job.ResumeService)
                info.Arguments += " --resume-service";
            if (updated)
                info.Arguments += " --update-job " + Quote(Path.GetFileName(job.Directory))
                    + " --update-token " + Quote(job.Token);
            return Process.Start(info);
        }

        private static ProcessStartInfo JavaStartInfo(UpdateJob job, bool windowed)
        {
            string javaBin = Path.Combine(job.BaseDirectory, "jre", "bin");
            var info = new ProcessStartInfo {
                FileName = Path.Combine(javaBin, windowed ? "javaw.exe" : "java.exe"),
                Arguments = "-Dfile.encoding=UTF-8 -jar " + Quote(Path.Combine(job.BaseDirectory, "java-airplay-launcher.jar"))
                    + " --base-dir " + Quote(job.BaseDirectory),
                WorkingDirectory = job.BaseDirectory, UseShellExecute = false,
                CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden
            };
            info.EnvironmentVariables["PATH"] = javaBin + ";" + Path.Combine(job.BaseDirectory, "gstreamer", "bin")
                + ";" + (Environment.GetEnvironmentVariable("PATH") ?? "");
            info.EnvironmentVariables["GST_PLUGIN_PATH"] = Path.Combine(job.BaseDirectory, "gstreamer", "lib", "gstreamer-1.0");
            return info;
        }

        internal static void WaitForStartup(UpdateJob job, Process replacement)
        {
            var waiting = Stopwatch.StartNew();
            while (waiting.Elapsed < TimeSpan.FromSeconds(60))
            {
                if (replacement.WaitForExit(100))
                    throw new IOException("The new launcher exited before startup completed.");
                if (UpdatePaths.MatchesMarker(Path.Combine(job.Directory, "started"), job.Token))
                    return;
            }
            throw new IOException("The new launcher did not acknowledge startup.");
        }

        internal static void StopChild(Process child)
        {
            if (child == null || child.HasExited)
                return;
            child.Kill();
            if (!child.WaitForExit(10000))
                throw new IOException("The new launcher could not be stopped safely; backups were retained.");
        }

        private static void Log(UpdateJob job, string text)
        {
            lock (LogLock)
            {
                try
                {
                    File.AppendAllText(Path.Combine(job.Directory, "update.log"),
                        DateTime.UtcNow.ToString("o", CultureInfo.InvariantCulture) + " " + text + "\r\n", new UTF8Encoding(false));
                }
                catch (IOException) { }
                catch (UnauthorizedAccessException) { }
            }
        }

        internal static string Quote(string argument)
        {
            var quoted = new StringBuilder("\"");
            int backslashes = 0;
            foreach (char character in argument)
            {
                if (character == '\\')
                {
                    backslashes++;
                    continue;
                }
                quoted.Append('\\', character == '"' ? backslashes * 2 + 1 : backslashes);
                quoted.Append(character);
                backslashes = 0;
            }
            return quoted.Append('\\', backslashes * 2).Append('"').ToString();
        }
    }

    internal sealed class UpdateJob
    {
        internal string BaseDirectory;
        internal string Directory;
        internal string Token;
        internal int ParentPid;
        internal long ParentStarted;
        internal bool ResumeService;
        internal bool Chinese;

        internal static UpdateJob Load(string jobDirectory)
        {
            string absolute = Path.GetFullPath(jobDirectory).TrimEnd(Path.DirectorySeparatorChar);
            string baseDirectory = Path.GetDirectoryName(Path.GetDirectoryName(absolute));
            UpdatePaths.RequireJob(baseDirectory, absolute);
            UpdatePaths.RequireOrdinaryPath(Path.Combine(absolute, "job.xml"));
            var settings = new XmlReaderSettings {
                DtdProcessing = DtdProcessing.Ignore, XmlResolver = null, MaxCharactersInDocument = 65536
            };
            var document = new XmlDocument { XmlResolver = null };
            using (XmlReader reader = XmlReader.Create(Path.Combine(absolute, "job.xml"), settings))
                document.Load(reader);
            if (document.DocumentElement == null || document.DocumentElement.Name != "properties")
                throw new IOException("Invalid update metadata.");
            var values = new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (XmlNode entry in document.DocumentElement.ChildNodes)
            {
                if (entry.NodeType == XmlNodeType.Comment || entry.Name == "comment")
                    continue;
                if (entry.Name != "entry" || entry.Attributes["key"] == null)
                    throw new IOException("Invalid update metadata entry.");
                values.Add(entry.Attributes["key"].Value, entry.InnerText);
            }
            if (!String.Equals(Path.GetFullPath(values["base"]), baseDirectory, StringComparison.OrdinalIgnoreCase)
                || !UpdatePaths.IsIdentifier(values["token"]))
                throw new IOException("Invalid update identity.");
            return new UpdateJob {
                BaseDirectory = baseDirectory, Directory = absolute, Token = values["token"],
                ParentPid = Int32.Parse(values["parentPid"], CultureInfo.InvariantCulture),
                ParentStarted = Int64.Parse(values["parentStarted"], CultureInfo.InvariantCulture),
                ResumeService = Boolean.Parse(values["resumeService"]), Chinese = values["language"] == "ZH_CN"
            };
        }
    }
}
