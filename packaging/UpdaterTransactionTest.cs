using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Xml;

namespace AirPlayLauncher.Update
{
    internal static class UpdaterTransactionTest
    {
        private static int Main(string[] arguments)
        {
            if (Array.IndexOf(arguments, "--base-dir") >= 0)
                return MockRuntime(arguments);
            try
            {
                SuccessfulUpdatePreservesSettingsAndBackup();
                ValidationAndStartupFailuresRollBack();
                LockedFilesRollBackEarlierMoves();
                MissingFilesDoNotTouchInstallation();
                PathsCannotEscapeOrUseJunctions();
                MetadataReadsJavaPropertiesSafely();
                ArgumentsAreQuoted();
                RuntimeStartupProtocolCommitsOrRollsBack();
                Console.WriteLine("Native updater transaction tests passed");
                return 0;
            }
            catch (Exception failure)
            {
                Console.Error.WriteLine(failure);
                return 1;
            }
        }

        private static void SuccessfulUpdatePreservesSettingsAndBackup()
        {
            using (var fixture = new Fixture())
            {
                bool activated = false;
                UpdateTransaction.Apply(fixture.Base, fixture.Job,
                    delegate { fixture.AssertManaged(fixture.Base, "new"); },
                    delegate { activated = true; }, delegate { }, delegate(string text) { });
                Equal(true, activated, "new launcher activated");
                fixture.AssertManaged(fixture.Base, "new");
                fixture.AssertManaged(Path.Combine(fixture.Job, "backup"), "old");
                fixture.AssertSettings();
                ExpectFailure(delegate {
                    UpdateTransaction.Apply(fixture.Base, fixture.Job, delegate { }, delegate { }, delegate { }, delegate(string text) { });
                });
                fixture.AssertManaged(fixture.Base, "new");
            }
        }

        private static void ValidationAndStartupFailuresRollBack()
        {
            foreach (bool startupFails in new[] { false, true })
            {
                using (var fixture = new Fixture())
                {
                    bool stoppedReplacement = false;
                    try
                    {
                        UpdateTransaction.Apply(fixture.Base, fixture.Job,
                            delegate { if (!startupFails) throw new IOException("Mock validation failure"); },
                            delegate { throw new IOException("Mock startup failure"); },
                            delegate { stoppedReplacement = true; }, delegate(string text) { });
                        throw new Exception("A failing update was committed");
                    }
                    catch (UpdateApplyException expected)
                    {
                        Equal(true, expected.Restored, "rollback succeeded");
                    }
                    Equal(true, stoppedReplacement, "replacement stopped before rollback");
                    fixture.AssertManaged(fixture.Base, "old");
                    fixture.AssertManaged(Path.Combine(fixture.Job, "failed"), "new");
                    fixture.AssertSettings();
                }
            }
            using (var fixture = new Fixture())
            {
                File.Delete(Path.Combine(fixture.Base, "AirPlayReceiver.exe"));
                try
                {
                    UpdateTransaction.Apply(fixture.Base, fixture.Job,
                        delegate { throw new IOException("Mock failure"); }, delegate { }, delegate { }, delegate(string text) { });
                }
                catch (UpdateApplyException expected) { Equal(true, expected.Restored, "absent original is supported"); }
                Equal(false, File.Exists(Path.Combine(fixture.Base, "AirPlayReceiver.exe")), "rollback does not invent old files");
                fixture.AssertSettings();
            }
        }

        private static void LockedFilesRollBackEarlierMoves()
        {
            using (var fixture = new Fixture())
            {
                using (var locked = new FileStream(Path.Combine(fixture.Base, "java-airplay-launcher.jar"),
                    FileMode.Open, FileAccess.Read, FileShare.None))
                {
                    try
                    {
                        UpdateTransaction.Apply(fixture.Base, fixture.Job, delegate { }, delegate { }, delegate { }, delegate(string text) { });
                        throw new Exception("A locked launcher was replaced");
                    }
                    catch (UpdateApplyException expected) { Equal(true, expected.Restored, "partial update rolled back"); }
                }
                fixture.AssertManaged(fixture.Base, "old");
                fixture.AssertSettings();
            }
        }

        private static void MissingFilesDoNotTouchInstallation()
        {
            using (var fixture = new Fixture())
            {
                File.Delete(Path.Combine(fixture.Job, "payload", "java-airplay-launcher.jar"));
                ExpectFailure(delegate {
                    UpdateTransaction.Apply(fixture.Base, fixture.Job, delegate { }, delegate { }, delegate { }, delegate(string text) { });
                });
                Equal(false, Directory.Exists(Path.Combine(fixture.Job, "backup")), "preflight runs before backup");
                fixture.AssertManaged(fixture.Base, "old");
                fixture.AssertSettings();
            }
        }

        private static void PathsCannotEscapeOrUseJunctions()
        {
            using (var fixture = new Fixture())
            {
                ExpectFailure(delegate { UpdatePaths.RequireJob(fixture.Base, fixture.Root); });
                string outside = Path.Combine(fixture.Root, "untouched");
                Directory.CreateDirectory(outside);
                File.WriteAllText(Path.Combine(outside, "keep.txt"), "do not touch");
                string junction = Path.Combine(fixture.Base, "jre", "junction");
                var info = new ProcessStartInfo {
                    FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "cmd.exe"),
                    Arguments = "/d /c mklink /J " + UpdaterProgram.Quote(junction) + " " + UpdaterProgram.Quote(outside),
                    UseShellExecute = false, CreateNoWindow = true, WindowStyle = ProcessWindowStyle.Hidden,
                    RedirectStandardOutput = true, RedirectStandardError = true
                };
                using (Process creation = Process.Start(info))
                {
                    string output = creation.StandardOutput.ReadToEnd() + creation.StandardError.ReadToEnd();
                    creation.WaitForExit();
                    if (creation.ExitCode != 0)
                        throw new Exception("Could not create the test junction: " + output);
                }
                fixture.Links.Add(junction);
                ExpectFailure(delegate {
                    UpdateTransaction.Apply(fixture.Base, fixture.Job, delegate { }, delegate { }, delegate { }, delegate(string text) { });
                });
                Equal("do not touch", File.ReadAllText(Path.Combine(outside, "keep.txt")), "junction target is untouched");
                fixture.AssertManaged(fixture.Base, "old");
            }
        }

        private static void MetadataReadsJavaPropertiesSafely()
        {
            using (var fixture = new Fixture())
            {
                string token = Guid.NewGuid().ToString();
                string metadata = Path.Combine(fixture.Job, "job.xml");
                var settings = new XmlWriterSettings { Encoding = new UTF8Encoding(false), Indent = true };
                using (XmlWriter writer = XmlWriter.Create(metadata, settings))
                {
                    writer.WriteDocType("properties", null, "http://java.sun.com/dtd/properties.dtd", null);
                    writer.WriteStartElement("properties");
                    foreach (var entry in new Dictionary<string, string> {
                        { "base", fixture.Base }, { "token", token }, { "version", "1.2.3" },
                        { "parentPid", "123" }, { "parentStarted", "12345678" },
                        { "resumeService", "true" }, { "language", "ZH_CN" }
                    })
                    {
                        writer.WriteStartElement("entry");
                        writer.WriteAttributeString("key", entry.Key);
                        writer.WriteString(entry.Value);
                        writer.WriteEndElement();
                    }
                    writer.WriteEndElement();
                }
                UpdateJob job = UpdateJob.Load(fixture.Job);
                Equal(fixture.Base, job.BaseDirectory, "Unicode and XML-special path preserved");
                Equal(token, job.Token, "job token");
                Equal(true, job.ResumeService, "running service preference");
                Equal(true, job.Chinese, "language preference");
                File.WriteAllText(metadata, "<!DOCTYPE properties [<!ENTITY external SYSTEM 'file:///nonexistent'>]>"
                    + "<properties><entry key='base'>&external;</entry></properties>");
                try
                {
                    UpdateJob.Load(fixture.Job);
                    throw new Exception("External XML entities were accepted");
                }
                catch (XmlException) { }
            }
        }

        private static void ArgumentsAreQuoted()
        {
            Equal("\"a b\"", UpdaterProgram.Quote("a b"), "spaces quoted");
            Equal("\"C:\\app\\\\\"", UpdaterProgram.Quote("C:\\app\\"), "trailing slash doubled");
            Equal("\"a\\\"b\"", UpdaterProgram.Quote("a\"b"), "embedded quote escaped");
        }

        private static int MockRuntime(string[] arguments)
        {
            string baseDirectory = arguments[Array.IndexOf(arguments, "--base-dir") + 1];
            if (Array.IndexOf(arguments, "--validate") >= 0)
                return File.Exists(Path.Combine(baseDirectory, "simulate-validation-failure")) ? 2 : 0;
            if (File.Exists(Path.Combine(baseDirectory, "simulate-startup-failure")))
                return 3;
            string identifier = arguments[Array.IndexOf(arguments, "--update-job") + 1];
            string token = arguments[Array.IndexOf(arguments, "--update-token") + 1];
            string jobDirectory = Path.Combine(baseDirectory, UpdatePaths.WorkDirectory, identifier);
            UpdatePaths.WriteMarker(Path.Combine(jobDirectory, "started"), token);
            var waiting = Stopwatch.StartNew();
            while (waiting.Elapsed < TimeSpan.FromSeconds(15))
            {
                if (UpdatePaths.MatchesMarker(Path.Combine(jobDirectory, "commit"), token))
                {
                    File.WriteAllText(Path.Combine(baseDirectory, "relaunched.txt"), String.Join("\n", arguments));
                    return 0;
                }
                Thread.Sleep(50);
            }
            return 4;
        }

        private static void RuntimeStartupProtocolCommitsOrRollsBack()
        {
            foreach (string failureMode in new[] { "none", "validation", "startup" })
            {
                using (var fixture = new Fixture())
                {
                    string javaBin = Path.Combine(fixture.Job, "payload", "jre", "bin");
                    Directory.CreateDirectory(javaBin);
                    string executable = Assembly.GetExecutingAssembly().Location;
                    File.Copy(executable, Path.Combine(javaBin, "java.exe"));
                    File.Copy(executable, Path.Combine(javaBin, "javaw.exe"));
                    if (failureMode != "none")
                        File.WriteAllText(Path.Combine(fixture.Base, "simulate-" + failureMode + "-failure"), "test");
                    var job = new UpdateJob {
                        BaseDirectory = fixture.Base, Directory = fixture.Job,
                        Token = Guid.NewGuid().ToString(), ResumeService = true
                    };
                    Process replacement = null;
                    bool committed = false;
                    try
                    {
                        UpdateTransaction.Apply(fixture.Base, fixture.Job,
                            delegate { UpdaterProgram.ValidateInstallation(job); },
                            delegate {
                                replacement = UpdaterProgram.Launch(job, true);
                                UpdaterProgram.WaitForStartup(job, replacement);
                                UpdatePaths.WriteMarker(Path.Combine(job.Directory, "commit"), job.Token);
                                committed = true;
                            },
                            delegate { UpdaterProgram.StopChild(replacement); }, delegate(string text) { });
                        Equal("none", failureMode, "failing runtime must not commit");
                    }
                    catch (UpdateApplyException expected)
                    {
                        Equal(true, failureMode != "none", "healthy runtime must not roll back");
                        Equal(true, expected.Restored, "runtime failure rollback completed");
                    }
                    finally
                    {
                        if (replacement != null)
                        {
                            if (!replacement.WaitForExit(5000))
                                UpdaterProgram.StopChild(replacement);
                            replacement.Dispose();
                        }
                    }
                    Equal(failureMode == "none", committed, "startup commit status");
                    fixture.AssertManaged(fixture.Base, committed ? "new" : "old");
                    fixture.AssertSettings();
                    if (committed)
                    {
                        string arguments = File.ReadAllText(Path.Combine(fixture.Base, "relaunched.txt"));
                        Equal(true, arguments.Contains("--resume-service"), "running service is restored");
                        Equal(true, arguments.Contains("--updated-relaunch"), "relaunch mode is explicit");
                        Equal(true, arguments.Contains(fixture.Base), "Unicode and special-character base path preserved");
                    }
                }
            }
        }

        private static void ExpectFailure(Action action)
        {
            try { action(); }
            catch (IOException) { return; }
            throw new Exception("An unsafe update operation was accepted");
        }

        private static void Equal(object expected, object actual, string message)
        {
            if (!Object.Equals(expected, actual))
                throw new Exception(message + ": expected " + expected + ", got " + actual);
        }

        private sealed class Fixture : IDisposable
        {
            internal readonly string Root;
            internal readonly string Base;
            internal readonly string Job;
            internal readonly List<string> Links = new List<string>();
            private const string Settings = "server.name=我的电脑\r\ngui.language=zh-CN\r\ncustom.option=keep\r\n";

            internal Fixture()
            {
                Root = Path.GetFullPath(Path.Combine(Path.GetTempPath(), "airplay-updater-test-" + Guid.NewGuid().ToString("N")));
                Base = Path.Combine(Root, "程序 & app");
                Job = Path.Combine(Base, UpdatePaths.WorkDirectory, Guid.NewGuid().ToString());
                Directory.CreateDirectory(Path.Combine(Job, "payload"));
                WriteManaged(Base, "old");
                WriteManaged(Path.Combine(Job, "payload"), "new");
                File.WriteAllText(Path.Combine(Base, "application.properties"), Settings, new UTF8Encoding(false));
                File.WriteAllText(Path.Combine(Job, "payload", "application.properties"), "factory defaults");
                File.WriteAllText(Path.Combine(Base, "user-file.txt"), "keep");
            }

            private static void WriteManaged(string directory, string value)
            {
                foreach (string name in UpdateTransaction.ManagedNames)
                {
                    string target = Path.Combine(directory, name);
                    if (name == "jre" || name == "gstreamer")
                    {
                        Directory.CreateDirectory(target);
                        target = Path.Combine(target, "runtime.bin");
                    }
                    File.WriteAllText(target, value);
                }
            }

            internal void AssertManaged(string directory, string value)
            {
                foreach (string name in UpdateTransaction.ManagedNames)
                {
                    string target = Path.Combine(directory, name);
                    if (name == "jre" || name == "gstreamer")
                        target = Path.Combine(target, "runtime.bin");
                    Equal(value, File.ReadAllText(target), "runtime " + name);
                }
            }

            internal void AssertSettings()
            {
                Equal(Settings, File.ReadAllText(Path.Combine(Base, "application.properties"), Encoding.UTF8), "settings preserved");
                Equal("keep", File.ReadAllText(Path.Combine(Base, "user-file.txt")), "unmanaged user file preserved");
            }

            public void Dispose()
            {
                string temporary = Path.GetFullPath(Path.GetTempPath()).TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
                if (!Path.GetFullPath(Root).StartsWith(temporary, StringComparison.OrdinalIgnoreCase)
                    || !Path.GetFileName(Root).StartsWith("airplay-updater-test-", StringComparison.Ordinal))
                    throw new IOException("Refusing to delete an unexpected test directory.");
                UpdatePaths.RequireOrdinaryPath(Root);
                foreach (string link in Links)
                {
                    if (!Path.GetFullPath(link).StartsWith(Root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
                        throw new IOException("Test junction escaped the fixture.");
                    Directory.Delete(link);
                }
                Directory.Delete(Root, true);
            }
        }
    }
}
