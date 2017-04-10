package sbmltools;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;

//BEGIN_HEADER
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.net.MalformedURLException;

import assemblyutil.AssemblyUtilClient;
import assemblyutil.FastaAssemblyFile;
import assemblyutil.GetAssemblyParams;
import assemblyutil.SaveAssemblyParams;
import datafileutil.DataFileUtilClient;
import datafileutil.DownloadWebFileOutput;
import datafileutil.DownloadWebFileParams;
import kbasereport.CreateParams;
import kbasereport.KBaseReportClient;
import kbasereport.Report;
import kbasereport.ReportInfo;
import kbasereport.WorkspaceObject;
import net.sf.jfasta.FASTAElement;
import net.sf.jfasta.FASTAFileReader;
import net.sf.jfasta.impl.FASTAElementIterator;
import net.sf.jfasta.impl.FASTAFileReaderImpl;
import net.sf.jfasta.impl.FASTAFileWriter;
import pt.uminho.sysbio.biosynthframework.sbml.XmlMessage;
import pt.uminho.sysbio.biosynthframework.sbml.XmlSbmlModel;
import pt.uminho.sysbio.biosynthframework.sbml.XmlSbmlModelValidator;
import pt.uminho.sysbio.biosynthframework.sbml.XmlStreamSbmlReader;
//END_HEADER

/**
 * <p>Original spec-file module name: SBMLTools</p>
 * <pre>
 * A KBase module: SBMLTools
 * This sample module contains one small method - filter_contigs.
 * </pre>
 */
public class SBMLToolsServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;
    private static final String version = "0.0.1";
    private static final String gitUrl = "https://fxe@github.com/Fxe/kbase-SBMLTools.git";
    private static final String gitCommitHash = "b692b0ab1fe7eee207e178c8412193ff87558a65";

    //BEGIN_CLASS_HEADER
    private final URL callbackURL;
    private final Path scratch;
    //END_CLASS_HEADER

    public SBMLToolsServer() throws Exception {
        super("SBMLTools");
        //BEGIN_CONSTRUCTOR
        final String sdkURL = System.getenv("SDK_CALLBACK_URL");
        try {
            callbackURL = new URL(sdkURL);
            System.out.println("Got SDK_CALLBACK_URL " + callbackURL);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid SDK callback url: " + sdkURL, e);
        }
        scratch = Paths.get(super.config.get("scratch"));
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: filter_contigs</p>
     * <pre>
     * The actual function is declared using 'funcdef' to specify the name
     * and input/return arguments to the function.  For all typical KBase
     * Apps that run in the Narrative, your function should have the 
     * 'authentication required' modifier.
     * </pre>
     * @param   params   instance of type {@link sbmltools.SbmlImportParams SbmlImportParams}
     * @return   parameter "output" of type {@link sbmltools.FilterContigsResults FilterContigsResults}
     */
    @JsonServerMethod(rpc = "SBMLTools.filter_contigs", async=true)
    public FilterContigsResults filterContigs(SbmlImportParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        FilterContigsResults returnVal = null;
        //BEGIN filter_contigs
        
        // Print statements to stdout/stderr are captured and available as the App log
        System.out.println("Starting filter contigs. Parameters:");
        System.out.println(params);
        

        SbmlTools.validateSbmlImportParams(params);

        
//        final DataFileUtilClient fileUtilClient = new DataFileUtilClient(callbackURL, authPart);
//        fileUtilClient.setIsInsecureHttpConnectionAllowed(true);
//        
//        DownloadWebFileOutput webFileOutput = fileUtilClient.downloadWebFile(
//            new DownloadWebFileParams().withFileUrl(params.getUrl()));
//        final Path webFilePath = scratch.resolve(webFileOutput.getCopyFilePath());
        
        
//        InputStream is = url.openStream();
        final String workspaceName = params.getWorkspaceName();
        final String assyRef = params.getAssemblyInputRef();
        String reportText = "empty";
        SbmlTools tools = new SbmlTools(workspaceName, authPart, callbackURL, jsonRpcContext);
        
        final String newAssyRef = tools.filterContigs(assyRef, scratch);
        
        reportText = tools.importModel(params);
        

        final String resultText = "No changes\n" + reportText;
        System.out.println(resultText);
        
        
        

        
        // Step 5 - Build a Report and return
        
        final KBaseReportClient kbr = new KBaseReportClient(callbackURL, authPart);
        // see note above about bad practice
        kbr.setIsInsecureHttpConnectionAllowed(true);
        final ReportInfo report = kbr.create(new CreateParams().withWorkspaceName(workspaceName)
                .withReport(new Report().withTextMessage(resultText)
                        .withObjectsCreated(Arrays.asList(new WorkspaceObject()
                                .withDescription("Filtered contigs")
                                .withRef(newAssyRef)))));
        // Step 6: contruct the output to send back
        
        returnVal = new FilterContigsResults()
                .withAssemblyOutput(newAssyRef)
                .withNInitialContigs(0L)
                .withNContigsRemaining(0L)
                .withNContigsRemoved(-1L)
                .withReportName(report.getName())
                .withReportRef(report.getRef());

        System.out.println("returning:\n" + returnVal);
        //END filter_contigs
        return returnVal;
    }

    /**
     * <p>Original spec-file function name: import_model_xml</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link sbmltools.SbmlImportParams SbmlImportParams}
     * @return   parameter "output" of type {@link sbmltools.FilterContigsResults FilterContigsResults}
     */
    @JsonServerMethod(rpc = "SBMLTools.import_model_xml", async=true)
    public FilterContigsResults importModelXml(SbmlImportParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        FilterContigsResults returnVal = null;
        //BEGIN import_model_xml
      
      // Print statements to stdout/stderr are captured and available as the App log
      System.out.println("Starting filter contigs. Parameters:");
      System.out.println(params);
      
      SbmlTools.validateSbmlImportParams(params);
      final String workspaceName = params.getWorkspaceName();
      final String assyRef = params.getAssemblyInputRef();
      
      SbmlTools tools = new SbmlTools(workspaceName, authPart, callbackURL, jsonRpcContext);
      final String newAssyRef = tools.filterContigs(assyRef, scratch);
      
//      tools.saveData("mockmodel", "KBaseFBA.FBAModel");
      // Step 5 - Build a Report and return
      
      final KBaseReportClient kbr = new KBaseReportClient(callbackURL, authPart);
      // see note above about bad practice
      kbr.setIsInsecureHttpConnectionAllowed(true);
      final ReportInfo report = kbr.create(new CreateParams().withWorkspaceName(workspaceName)
              .withReport(new Report().withTextMessage(workspaceName + " " + newAssyRef + " " + tools.saveData("", ""))
                      .withObjectsCreated(Arrays.asList(new WorkspaceObject()
                              .withDescription("Filtered contigs")
                              .withRef(newAssyRef)))));
      // Step 6: contruct the output to send back
      
      returnVal = new FilterContigsResults()
          .withAssemblyOutput(newAssyRef)
          .withNInitialContigs(1L)
          .withNContigsRemaining(1L)
          .withNContigsRemoved(2L)
          .withReportName(report.getName())
          .withReportRef(report.getRef());

      System.out.println("returning:\n" + returnVal);
      
        //END import_model_xml
        return returnVal;
    }
    @JsonServerMethod(rpc = "SBMLTools.status")
    public Map<String, Object> status() {
        Map<String, Object> returnVal = null;
        //BEGIN_STATUS
        returnVal = new LinkedHashMap<String, Object>();
        returnVal.put("state", "OK");
        returnVal.put("message", "");
        returnVal.put("version", version);
        returnVal.put("git_url", gitUrl);
        returnVal.put("git_commit_hash", gitCommitHash);
        //END_STATUS
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new SBMLToolsServer().startupServer(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            JsonServerSyslog.setStaticUseSyslog(false);
            JsonServerSyslog.setStaticMlogFile(args[1] + ".log");
            new SBMLToolsServer().processRpcCall(new File(args[0]), new File(args[1]), args[2]);
        } else {
            System.out.println("Usage: <program> <server_port>");
            System.out.println("   or: <program> <context_json_file> <output_json_file> <token>");
            return;
        }
    }
}
