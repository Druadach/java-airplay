using System;
using System.Runtime.InteropServices;

namespace AirPlayLauncher
{
    static class LauncherArgumentTest
    {
        [DllImport("shell32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
        private static extern IntPtr CommandLineToArgvW(string command, out int count);

        [DllImport("kernel32.dll")]
        private static extern IntPtr LocalFree(IntPtr memory);

        static int Main()
        {
            string directory = @"C:\Program Files\AirPlay Receiver\";
            string jar = directory + "java-airplay-launcher.jar";
            string[] forwarded = { "--minimized", "--auto-start", "", "quoted \"value\"", @"ends with slash\" };
            string command = "javaw.exe " + Program.BuildJavaArguments(jar, directory, forwarded);
            int count;
            IntPtr parsed = CommandLineToArgvW(command, out count);
            if (parsed == IntPtr.Zero)
                throw new InvalidOperationException("Cannot parse Windows command line");
            try
            {
                string[] expected = { "javaw.exe", "-Dfile.encoding=UTF-8", "-jar", jar,
                    "--base-dir", directory, forwarded[0], forwarded[1], forwarded[2], forwarded[3], forwarded[4] };
                if (count != expected.Length)
                    throw new Exception("Argument count changed: " + count);
                for (int index = 0; index < count; index++)
                {
                    string actual = Marshal.PtrToStringUni(Marshal.ReadIntPtr(parsed, index * IntPtr.Size));
                    if (actual != expected[index])
                        throw new Exception("Argument " + index + " changed: " + actual);
                }
            }
            finally
            {
                LocalFree(parsed);
            }
            Console.WriteLine("Native launcher argument tests passed");
            return 0;
        }
    }
}
