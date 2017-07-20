package sbmltools;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import datafileutil.DataFileUtilClient;
import datafileutil.ObjectSaveData;
import datafileutil.SaveObjectsParams;
import kbasefba.Biomass;
import kbasefba.FBAModel;
import kbasereport.WorkspaceObject;
import pt.uminho.sysbio.biosynth.integration.io.dao.neo4j.MetaboliteMajorLabel;
import pt.uminho.sysbio.biosynthframework.kbase.FBAModelFactory;
import pt.uminho.sysbio.biosynthframework.kbase.KBaseIOUtils;
import pt.uminho.sysbio.biosynthframework.kbase.KBaseModelSeedIntegration;
import pt.uminho.sysbio.biosynthframework.sbml.MessageType;
import pt.uminho.sysbio.biosynthframework.sbml.XmlMessage;
import pt.uminho.sysbio.biosynthframework.sbml.XmlSbmlModel;
import pt.uminho.sysbio.biosynthframework.sbml.XmlSbmlModelValidator;
import pt.uminho.sysbio.biosynthframework.sbml.XmlStreamSbmlReader;
import pt.uminho.sysbio.biosynthframework.util.CollectionUtils;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.Tuple11;
import us.kbase.common.service.UObject;
import us.kbase.common.service.UnauthorizedException;

public class SbmlTools {

  private static final Logger logger = LoggerFactory.getLogger(SbmlTools.class);

  public static String DATA_EXPORT_PATH = "/data/integration/export";
  public static String CURATION_DATA = "/data/integration/cc/cpd_curation.tsv";
  public static String LOCAL_CACHE = "./cache";
  
  public final AuthToken authPart;
  public final RpcContext jsonRpcContext;
  public final URL callbackURL;
  public final String workspace;
  private final DataFileUtilClient dfuClient;
  public KBaseModelSeedIntegration modelSeedIntegration = null;
  
  
  public SbmlTools(String workspace, AuthToken authPart, URL callbackURL, RpcContext jsonRpcContext) throws UnauthorizedException, IOException {
    this.authPart = authPart;
    this.jsonRpcContext = jsonRpcContext;
    this.callbackURL = callbackURL;
    this.workspace = workspace;
    this.modelSeedIntegration = new KBaseModelSeedIntegration(DATA_EXPORT_PATH, CURATION_DATA);
    if (callbackURL != null) {
      this.dfuClient = new DataFileUtilClient(callbackURL, authPart);
      dfuClient.setIsInsecureHttpConnectionAllowed(true);
    } else {
      logger.warn("missing callbackURL no saving");
      this.dfuClient = null;
    }
  }

  public static void validateSbmlImportParams(SbmlImportParams params) {
    /* Step 1 - Parse/examine the parameters and catch any errors
     * It is important to check that parameters exist and are defined, and that nice error
     * messages are returned to users.  Parameter values go through basic validation when
     * defined in a Narrative App, but advanced users or other SDK developers can call
     * this function directly, so validation is still important.
     */
    final String workspaceName = params.getWorkspaceName();
    if (workspaceName == null || workspaceName.isEmpty()) {
      throw new IllegalArgumentException(
          "Parameter workspace_name is not set in input arguments");
    }
    final String assyRef = params.getAssemblyInputRef();
    if (assyRef == null || assyRef.isEmpty()) {
      throw new IllegalArgumentException(
          "Parameter assembly_input_ref is not set in input arguments");
    }
    if (params.getMinLength() == null) {
      throw new IllegalArgumentException(
          "Parameter min_length is not set in input arguments");
    }
    final long minLength = params.getMinLength();
    if (minLength < 0) {
      throw new IllegalArgumentException("min_length parameter cannot be negative (" +
          minLength + ")");
    }
  }

  public static class ImportModelResult {
    public String message = "";
    public String modelRef = "";
    public List<WorkspaceObject> objects = new ArrayList<> ();
  }

  public static void xrxnAttributes(XmlSbmlModelValidator validator) {
    String[] xrxnAttr = new String[] {
        "upperFluxBound", "fast", "metaid", "reversible", "sboTerm", "name", "lowerFluxBound", "id"
    };
    String[] xrxnSpecieAttr = new String[] {
        "stoichiometry", "constant", "species", "metaid", "sboTerm"
    };
    validator.xrxnAttr.addAll(Arrays.asList(xrxnAttr));
    validator.xrxnStoichAttr.addAll(Arrays.asList(xrxnSpecieAttr));
  }

  public static Map<String, MessageType> knownSpecieAttributes() {
    String[] attr = new String[] {
        "speciesType", "NONE",
        "charge", "NONE",
        "constant", "NONE",
        "metaid", "NONE",
        "hasOnlySubstanceUnits", "NONE",
        "sboTerm", "NONE",
        "boundaryCondition", "NONE",
        "chemicalFormula", "NONE",
        "initialAmount", "NONE",
        "name", "NONE",
        "compartment", "WARN",
        "id", "CRITICAL",
        "initialConcentration", "NONE",
    };
    Map<String, MessageType> fields = new HashMap<>();
    for (int i = 0; i < attr.length; i+=2) {
      fields.put(attr[i], MessageType.valueOf(attr[i + 1]));
    }
    return fields;
  }
  
  
  
  public static String fetchAndCache(String urlString) {
    File file2 = null;
    URLConnection connection = null;
    OutputStream os = null;
    try {
      URL url = new URL(urlString);
      connection = url.openConnection();
      
      String uuid = UUID.randomUUID().toString();
      File file = new File(String.format("%s/%s", LOCAL_CACHE, uuid));
      if (file.mkdirs()) {
        logger.info("created {}", file.getAbsolutePath());
      }
      file2 = new File(String.format("%s/%s", file.getAbsolutePath(), uuid));
      os = new FileOutputStream(file2);
      IOUtils.copy(connection.getInputStream(), os);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      IOUtils.close(connection);
      IOUtils.closeQuietly(os);
    }
    
    if (file2 != null) {
      return file2.getAbsolutePath();
    }
    
    return null;
  }
  
  public static String status2(Map<String, Map<MetaboliteMajorLabel, String>> imap, int total) {
    Map<Object, Integer> counter = new HashMap<> ();
    Map<Object, Double> cover = new HashMap<> ();
    
    for (String e : imap.keySet()) {
      Map<MetaboliteMajorLabel, String> a = imap.get(e);
      boolean any = false;
      for (MetaboliteMajorLabel db : a.keySet()) {
        String refs = a.get(db);
        if (refs != null && !refs.isEmpty()) {
          any = true;
          CollectionUtils.increaseCount(counter, db, 1);
        }
      }
      if (any) {
        CollectionUtils.increaseCount(counter, "has_reference", 1);
      }
    }
    
    for (Object k : counter.keySet()) {
      int count = counter.get(k);
      cover.put(k, (double) count / total);
    }
    return Joiner.on(", ").withKeyValueSeparator(": ").join(cover);
  }
  
  public void importModel(InputStream is, 
      ImportModelResult result, String modelId, String url, boolean runIntegration, Collection<String> biomassIds) throws Exception {
    //import
    FBAModel model = null;
    XmlStreamSbmlReader reader = new XmlStreamSbmlReader(is);
    XmlSbmlModel xmodel = reader.parse();
    
    if (modelId == null || modelId.trim().isEmpty()) {
      modelId = getNameFromUrl(url);
      logger.info("auto model id: {}", modelId);
    }
    
    logger.info("validate");
    XmlSbmlModelValidator validator = new XmlSbmlModelValidator(xmodel, knownSpecieAttributes());
    xrxnAttributes(validator);
    
    List<XmlMessage> msgs = validator.validate();
    result.message += "\n" + String.format("Species %d, Reactions %s, %s", 
        xmodel.getSpecies().size(), 
        xmodel.getReactions().size(), 
        url);
    //      String txt = "";
    Map<MessageType, Integer> typeCounterMap = new HashMap<> ();
    for (XmlMessage m : msgs) {
      CollectionUtils.increaseCount(typeCounterMap, m.type, 1);
    }
    if (!typeCounterMap.isEmpty()) {
      result.message +="\n" + modelId + " "+ String.format("%s", Joiner.on(' ').withKeyValueSeparator(": ").join(typeCounterMap));
    }
    
    

    
    modelId = modelId.trim();
    
    Map<String, String> spiToModelSeedReference = new HashMap<> ();
    
    //check if integrate
    if (runIntegration) {
      
//      String imodelEntry = "i" + modelId;
//      KBaseModelSeedIntegration integration = new KBaseModelSeedIntegration(DATA_EXPORT_PATH, CURATION_DATA);
      Map<String, Map<MetaboliteMajorLabel, String>> imap = 
          modelSeedIntegration.generateDatabaseReferences(xmodel, modelId);
      //get stats
      result.message +="\n" + modelId + " " + status2(imap, xmodel.getSpecies().size());
      spiToModelSeedReference = modelSeedIntegration.spiToModelSeedReference;
      result.message += String.format("\ni: %d", spiToModelSeedReference.size());
//      FBAModel ikmodel = new FBAModelFactory()
//          .withModelSeedReference(spiToModelSeedReference)
//          .withBiomassIds(biomassIds)
//          .withXmlSbmlModel(xmodel)
//          .withModelId(imodelEntry)
//          .build();
      
//      String imodelRef = this.saveDataSafe(imodelEntry, 
//          KBaseType.FBAModel.value(), ikmodel, dfuClient);
//      result.objects.add(new WorkspaceObject().withDescription("i model")
//          .withRef(imodelRef));
    }
    
    model = new FBAModelFactory()
        .withBiomassIds(biomassIds)
        .withXmlSbmlModel(xmodel)
        .withModelId(modelId)
        .withModelSeedReference(spiToModelSeedReference)
        .build();
    
    if (!model.getBiomasses().isEmpty()) {
      Set<String> biomass = new HashSet<> ();
      for (Biomass b : model.getBiomasses()) {
        biomass.add(b.getId());
      }
      result.message +="\n" + modelId + " biomass: " + biomass;
    }
    
    String fbaModelRef = KBaseIOUtils.saveDataSafe(modelId, 
        KBaseType.FBAModel.value(), model, workspace, dfuClient);
    result.objects.add(new WorkspaceObject().withDescription(url)
        .withRef(fbaModelRef));
  }
  
  public static class ZipContainer implements Closeable {
    
    private InputStream is = null; //file input stream
    private ZipInputStream zis = null; //zip file manipulator
    private ZipFile zf = null; //zip file pointer
//    InputStream rfis = null; //file within zip file pointer
    
    public ZipContainer(String path) throws IOException {
      this.is = new FileInputStream(path);
      this.zis = new ZipInputStream(is);
      this.zf = new ZipFile(path);
    }
    
    public List<Map<String, Object>> getInputStreams() throws IOException {
      List<Map<String, Object>> streams = new ArrayList<> ();
      ZipEntry ze = null;
      while ((ze = zis.getNextEntry()) != null) {
        Map<String, Object> record = new HashMap<> ();
        record.put("name", ze.getName());
        record.put("size", ze.getSize());
        record.put("compressed_size", ze.getCompressedSize());
        record.put("method", ze.getMethod());
        record.put("is", zf.getInputStream(ze));
        streams.add(record);
      }
      
      return streams;
    }
    
    @Override
    public void close() throws IOException {
      IOUtils.closeQuietly(this.zf);
      IOUtils.closeQuietly(this.zis);
      IOUtils.closeQuietly(this.is);
    }
    
  }
  
  public void aaa() {

  }

  public ImportModelResult importModel(SbmlImporterParams params) {
    ImportModelResult result = new ImportModelResult();

    logger.debug("SbmlImporterParams:{} ", params);

    result.message = String.format("%s %s %d %s",
        params.getSbmlUrl(),
        params.getBiomass(),
        params.getAutomaticallyIntegrate(),
        params.getModelName());
    ZipContainer container = null;
    
    try {

      String urlPath = params.getSbmlUrl();
      //check url type
      String localPath = fetchAndCache(params.getSbmlUrl());
      if (localPath == null) {
        throw new IOException("unable to create temp file");
      }
      boolean runIntegration = params.getAutomaticallyIntegrate() == 1;
      String modelId = params.getModelName();
      List<String> biomass = params.getBiomass();
      if (biomass == null) {
        biomass = new ArrayList<> ();
      }
      Map<String, InputStream> inputStreams = new HashMap<> ();
      
      if (urlPath.endsWith(".zip")) {
        container = new ZipContainer(localPath);
        List<Map<String, Object>> streams = container.getInputStreams();
        for (Map<String, Object> d : streams) {
          InputStream is = (InputStream) d.get("is");
          String u = params.getSbmlUrl() + "/" + (String) d.get("name");
          inputStreams.put(u, is);
        }
        //ignore modelId when multiple models
        if (inputStreams.size() > 1) {
          modelId = null;
        }
      } else {
        inputStreams.put(params.getSbmlUrl(), new FileInputStream(localPath));
      }
      
      for (String u : inputStreams.keySet()) {
        InputStream is = inputStreams.get(u);
        try {
          importModel(is, result, modelId, u, runIntegration, biomass);
        } catch (Exception e) {
          result.message += "\nERROR: " + u + " " + e.getMessage();
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
      logger.error("{}", e.getMessage());
    } finally {
      IOUtils.closeQuietly(container);
    }

    return result;
  }

  public ImportModelResult importModel(SbmlImportParams params) {

    logger.info("run");
    ImportModelResult result = new ImportModelResult();
    String reportText = "";
    try {
      URL url = new URL(params.getUrl());
      URLConnection connection = url.openConnection();
      logger.info("read");
      //      URL url = new URL(params.getUrl());
      XmlStreamSbmlReader reader = new XmlStreamSbmlReader(connection.getInputStream());
      XmlSbmlModel xmodel = reader.parse();
      //      msg = model.getAttributes().toString();
      logger.info("validate");
      XmlSbmlModelValidator validator = new XmlSbmlModelValidator(xmodel, knownSpecieAttributes());
      xrxnAttributes(validator);

      List<XmlMessage> msgs = validator.validate();
      reportText = String.format("Species %d, Reactions %s, %s", xmodel.getSpecies().size(), xmodel.getReactions().size(), params.getUrl());
      //      String txt = "";
      for (XmlMessage m : msgs) {
        reportText +="\n" + String.format("%s", m);
      }

      //      xmlsbmlmodelf
      //      reportText += SbmlTools.aaa(validator.validate());
      //      reportText = String.format("Species %d, Reactions %s, %s", model.getSpecies().size(), model.getReactions().size(), params.getUrl());

      connection.getInputStream().close();

      String modelId = getNameFromUrl(params.getUrl());
      FBAModel kmodel = new FBAModelFactory()
          .withXmlSbmlModel(xmodel)
          .withModelId(modelId)
          .build();

//      String a = "/data/integration/export";
//      String b = "/data/integration/cc/cpd_curation.tsv";
      //      a = "/var/biobase/export";
      //      b = "/var/biobase/integration/cc/cpd_curation.tsv";
      boolean autoIntegration = true;
      if (autoIntegration) {
//        //make integrated model
//        String imodelEntry = "i" + modelId;
//        KBaseModelSeedIntegration integration = new KBaseModelSeedIntegration(a, b);
//        integration.generateDatabaseReferences(xmodel, imodelEntry);
//        Map<String, String> spiToModelSeedReference = integration.spiToModelSeedReference;
//        reportText += String.format("\ni: %d", spiToModelSeedReference.size());
//        FBAModel ikmodel = new FBAModelFactory()
//            .withModelSeedReference(spiToModelSeedReference)
//            .withXmlSbmlModel(xmodel)
//            .withModelId(imodelEntry)
//            .build();

//        String imodelRef = this.saveData(imodelEntry, KBaseType.FBAModel.value(), ikmodel);
      }

      Object kmedia = MockData.mockMedia();
      this.saveData("mock_media2", KBaseType.KBaseBiochemMedia.value(), kmedia);
      logger.info("save model [{}]", modelId);
      String modelRef = this.saveData(modelId, KBaseType.FBAModel.value(), kmodel);

      //      FbaToolsClient fbaToolsClient = new FbaToolsClient(callbackURL, authPart);
      //      RunFluxBalanceAnalysisParams fbaParams = new RunFluxBalanceAnalysisParams()
      //          .withFbamodelId(modelId)
      //          .withFbamodelWorkspace("")
      //          .withMediaId("mock_media");
      //      fbaToolsClient.runFluxBalanceAnalysis(fbaParams);
      result.modelRef = modelRef;
    } catch (Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      reportText += e.getMessage() + " " + sw.toString();
    }

    logger.info("import model [done]");

    result.message = reportText;

    return result;
  }




  @Deprecated
  public String saveData(String nameId, String dataType, Object o) throws Exception {
    //  Object o = null;
    //  String nameId = "";
    //  String dataType = "";
    final DataFileUtilClient dfuClient = new DataFileUtilClient(callbackURL, authPart);
    dfuClient.setIsInsecureHttpConnectionAllowed(true);
    long wsId = dfuClient.wsNameToId(workspace);

    SaveObjectsParams params = new SaveObjectsParams()
        .withId(wsId)
        .withObjects(Arrays.asList(
            new ObjectSaveData().withName(nameId)
            .withType(dataType)
            .withData(new UObject(o))));
    ////  params.setId(wsId);
    ////  List<ObjectSaveData> saveData = new ArrayList<> ();
    ////  ObjectSaveData odata = new ObjectSaveData();
    ////  odata.set
    ////  
    ////  params.setObjects(saveData);
    ////  ;
    String ref = KBaseIOUtils.getRefFromObjectInfo(dfuClient.saveObjects(params).get(0));

    return ref;
  }
  


  public static String getNameFromUrl(String urlStr) {
    String[] strs = urlStr.split("/");
    String last = strs[strs.length - 1];
    if (last.contains(".")) {
      last = last.substring(0, last.indexOf('.'));
    }
    return last;
  }

  //  public FBAModel convertModel(XmlSbmlModel xmodel, String modelId) {
  //    
  //
  //    return model;
  //  }


}
