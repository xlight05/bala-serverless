package com.bala.azf;

import com.bala.azf.exceptions.BalaNotFoundException;
import com.bala.azf.exceptions.BuildException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.bala.BalaProject;
import io.ballerina.projects.environment.Environment;
import io.ballerina.projects.environment.EnvironmentBuilder;
import io.ballerina.projects.repos.TempDirCompilationCache;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Azure Functions with Azure Storage Queue trigger.
 */
public class QueueTriggerJava1 {

   private static final Path BALLERINA_HOME = Path.of("/home/ballerina-2201.4.1-swan-lake/distributions/ballerina-2201.4.1");
    // private static final Path BALLERINA_HOME = Path.of("/usr/lib/ballerina/distributions/ballerina-2201.4.1");

    private static final Path USER_HOME = Paths.get(System.getenv("USER_HOME") == null ?
            System.getProperty("user.home") : System.getenv("USER_HOME"));
//    private static final Path USER_HOME = Paths.get("/home/ballerina");

    /**
     * This function will be invoked when a new message is received at the specified path. The message contents are provided as input to this function.
     */
   @FunctionName("QueueTriggerJava1")
   public void run(
       @QueueTrigger(name = "balaUrl", queueName = "2201-4-0", connection = "AzureWebJobsStorage") String balaUrl,
       final ExecutionContext context) {
       context.getLogger().info("Java Queue trigger function processed a message: " + balaUrl);
       context.getLogger().info("Ballerina home: " + BALLERINA_HOME);
       context.getLogger().info("User home: " + USER_HOME);
       context.getLogger().info("Default Charset " +  Charset.defaultCharset());
       long currentTime = System.nanoTime();
       try {
           Path tempPath = Files.createTempDirectory("bala-" + currentTime).toAbsolutePath();
           Path balaPath = tempPath.resolve(currentTime + ".bala");

           Files.createFile(balaPath);

           // Download the bala file and save
           downloadBala(balaUrl, balaPath);

           // Load bala as a project
           BalaProject balaProject = buildBala(balaPath);
           context.getLogger().info("Package is compiled " + balaProject.currentPackage().packageName().value());
       } catch (Exception e) {
            context.getLogger().info("Error occurred while processing bala " + e.getMessage());
            e.printStackTrace();
       }
   }

//   public static void main (String[] args) {
//       String balaUrl = "https://balahandlerprem9663.blob.core.windows.net/bala-store/anjana-http_mime-any-0.1.0.bala";
//       long currentTime = System.nanoTime();
//       System.out.println("Default Charset " +  Charset.defaultCharset());
//       try {
//           Path tempPath = Files.createTempDirectory("bala-" + currentTime).toAbsolutePath();
//           Path balaPath = tempPath.resolve(currentTime + ".bala");
//
//           Files.createFile(balaPath);
//
//           // Download the bala file and save
//           downloadBala(balaUrl, balaPath);
//
//           // Load bala as a project
//           BalaProject balaProject = buildBala(balaPath);
//           System.out.println("Package isn compiled " + balaProject.currentPackage().packageName().value());
//       } catch (Exception e) {
//           System.out.println("Error occurred while processing bala " + e.getMessage());
//       }
//   }

    /**
     * Download the bala file from URL.
     *
     * @param balaURL  URL for the bala file.
     * @param balaPath The output path of the bala file.
     * @throws BalaNotFoundException When bala URL is invalid or cannot be accessed.
     */
    private static void downloadBala(String balaURL, Path balaPath) throws BuildException, BalaNotFoundException {
        try {
            URL balaURI = new URL(balaURL);
            ReadableByteChannel readableByteChannel = Channels.newChannel(balaURI.openStream());
            FileOutputStream fileOutputStream = new FileOutputStream(balaPath.toFile());
            fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (FileNotFoundException e) {
            throw new BuildException("unable to write to temporary bala file: " + balaPath, e);
        } catch (MalformedURLException e) {
            throw new BalaNotFoundException("unable to locate bala file: " + balaURL, e);
        } catch (IOException e) {
            throw new BuildException("error reading from '" + balaURL + "' or writing to '" + balaPath + "'", e);
        }
    }

    /**
     * Building a bala file.
     *
     * @param balaPath The bala file.
     * @return The built project.
     * @throws BuildException When error occurred while building the bala.
     */
    public static BalaProject buildBala(Path balaPath) throws BuildException {
        try {
            // Remove this condition when https://github.com/ballerina-platform/ballerina-lang/issues/29169 is resolved
            if (Files.notExists(USER_HOME)) {
                Files.createDirectories(USER_HOME);
            }
            System.out.println("Before Creating the environment");
            Environment environment = EnvironmentBuilder.getBuilder()
                    .setBallerinaHome(BALLERINA_HOME)
                    .setUserHome(USER_HOME).build();
            System.out.println("After Creating the environment");
            ProjectEnvironmentBuilder defaultBuilder = ProjectEnvironmentBuilder.getBuilder(environment);
            System.out.println("After Creating the builder");
            defaultBuilder.addCompilationCacheFactory(TempDirCompilationCache::from);
            System.out.println("After cache factory");
            return BalaProject.loadProject(defaultBuilder, balaPath);
        } catch (Exception e) {
            throw new BuildException("error occurred when building: " + e.getMessage(), e);
        }
    }
}
