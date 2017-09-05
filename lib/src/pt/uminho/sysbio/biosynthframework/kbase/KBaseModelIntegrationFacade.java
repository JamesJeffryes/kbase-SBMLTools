package pt.uminho.sysbio.biosynthframework.kbase;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import datafileutil.DataFileUtilClient;
import kbasefba.FBAModel;
import kbasereport.CreateParams;
import kbasereport.KBaseReportClient;
import kbasereport.Report;
import kbasereport.ReportInfo;
import kbasereport.WorkspaceObject;
import pt.uminho.sysbio.biosynthframework.kbase.KBaseHtmlReport.ReportFiles;
import sbmltools.CompartmentMapping;
import sbmltools.IntegrateModelParams;
import sbmltools.KBaseType;
import sbmltools.SbmlImporterResults;
import us.kbase.workspace.WorkspaceClient;

public class KBaseModelIntegrationFacade {
  
  private static final Logger logger = LoggerFactory.getLogger(KBaseModelIntegrationFacade.class);
  
  private final WorkspaceClient wspClient;
  private final DataFileUtilClient dfuClient;
  private final KBaseReportClient kbrClient;
  private final KBaseBiodbContainer biodbContainer;
  private final KBaseGeneIntegration geneIntegration;
  private final Path scratch;
  
  public KBaseModelIntegrationFacade(WorkspaceClient    wspClient,
                                     DataFileUtilClient dfuClient, 
                                     KBaseReportClient  kbrClient,
                                     KBaseGeneIntegration geneIntegration,
                                     String biodbPath,
                                     Path scratch) {
    
    this.wspClient = wspClient;
    this.dfuClient = dfuClient;
    this.kbrClient = kbrClient;
    this.geneIntegration = geneIntegration;
    this.biodbContainer = new KBaseBiodbContainer(biodbPath);
    this.scratch = scratch;
  }
  
  public static Map<String, String> getCompartmentMapping(List<CompartmentMapping> compartmentMappings) {
    Map<String, String> result = new HashMap<> ();

    for (CompartmentMapping cmap : compartmentMappings) {
      if (cmap != null && cmap.getKbaseCompartmentId() != null && 
          cmap.getModelCompartmentId() != null && 
          !cmap.getModelCompartmentId().isEmpty()) {
        String to = cmap.getKbaseCompartmentId();
        String from = cmap.getModelCompartmentId().iterator().next();
        result.put(from, to);
      }
    }

    return result;
  }
  
//  public SbmlImporterResults kbaseIntegrate(IntegrateModelParams params, Long workspaceId) throws Exception {
//    //validate params
//    String fbaModelName = params.getModelName();
//    String outputName = params.getOutputModelName();
//    Long workspaceId = dfuClient.wsNameToId(workspaceName);
//    System.out.println(workspaceId);
//    Map<String, String> compartmentMapping = getCompartmentMapping(params.getCompartmentTranslation());
//    
//    //get model
//    FBAModel fbaModel = KBaseIOUtils.getObject(fbaModelName, workspaceName, null, FBAModel.class, wspClient);
//    //get genome ref
//    KBaseIOUtils.getObject(params.getGenomeId(), workspaceName, null, wspClient);
//    
//    
//    
//    //integrate
//    KBaseIntegration integration = new KBaseIntegration();
//    integration.fbaModel = fbaModel;
//    integration.compartmentMapping = compartmentMapping;
//    integration.rename = "ModelSeed";
//    integration.fillMetadata = true;
//    integration.biodbContainer = this.biodbContainer;
//    
//    integration.integrate();
//    
//    
//    String geneData = "";
//    if (geneIntegration != null) {
//      geneData = geneIntegration.searchGenome(fbaModel);
//    }
//    
//    
//    String ref = KBaseIOUtils.saveDataSafe(outputName, KBaseType.FBAModel, fbaModel, workspaceName, dfuClient);
//    
//    List<WorkspaceObject> wsObjects = new ArrayList<> ();
//    wsObjects.add(new WorkspaceObject().withDescription("model").withRef(ref));
//    final ReportInfo reportInfo = kbrClient.create(
//        new CreateParams().withWorkspaceName(workspaceName)
//                          .withReport(new Report()
//                              .withObjectsCreated(wsObjects)
//                              .withTextMessage(String.format("%s\n%s", params, geneData))));
//    
//    SbmlImporterResults returnVal = new SbmlImporterResults().withFbamodelId(outputName)
//                                                             .withReportName(reportInfo.getName())
//                                                             .withReportRef(reportInfo.getRef());
//    
//    return returnVal;
//  }
  
  public SbmlImporterResults kbaseIntegrate(IntegrateModelParams params, String workspaceName) throws Exception {
    //validate params
    System.out.println(params);
    String fbaModelName = params.getModelName();
    String outputName = params.getOutputModelName();
//    Long workspaceId = dfuClient.wsNameToId(workspaceName);
//    System.out.println(workspaceId);
    Map<String, String> compartmentMapping = getCompartmentMapping(params.getCompartmentTranslation());
    
    //get model
    FBAModel fbaModel = KBaseIOUtils.getObject(fbaModelName, workspaceName, null, FBAModel.class, wspClient);
    //get genome ref
    KBaseIntegrationReport kir = new KBaseIntegrationReport();
    kir.model = fbaModel.getId();
    kir.objName = fbaModel.getName();
    
    Pair<KBaseId, Object> kdata = KBaseIOUtils.getObject2(params.getGenomeId(), workspaceName, null, wspClient);
    
    Set<String> biomassReactions = new HashSet<> ();
    if (params.getBiomassReactions() != null) {
      for (String s : params.getBiomassReactions().split(",")) {
        if (s.trim() != null) {
          biomassReactions.add(s.trim());
        }
      }
    }
    
    //integrate
    KBaseIntegration integration = new KBaseIntegration(fbaModel);
    integration.report = kir;
    integration.biomassSet.addAll(biomassReactions);
    integration.genomeRef = kdata.getLeft().reference;
    integration.genome = KBaseUtils.convert(kdata.getRight(), KBaseGenome.class);
    integration.compartmentMapping = compartmentMapping;
    integration.rename = params.getTranslateDatabase();
    integration.fillMetadata = params.getFillMetadata() == 1L;
    integration.mediaName = params.getOutputMediaName();
    
    integration.biodbContainer = this.biodbContainer;
    integration.integrate();
    
    try {
      if (params.getTemplateId() != null) {
        integration.adapter.setTemplate(params.getTemplateId(), wspClient);
      }
    } catch (Exception e) {
      logger.error("Set Template: [{}] - {}", params.getTemplateId(), e.getMessage());
    }
    
    String geneData = "";
    if (geneIntegration != null) {
      geneData = geneIntegration.searchGenome(fbaModel);
    }
    
    kir.fillGenomeData(geneIntegration);
    
    KBaseId mediaKid = null;
    if (integration.defaultMedia != null) {
      mediaKid = KBaseIOUtils.saveData(params.getOutputMediaName(), KBaseType.KBaseBiochemMedia.value(), integration.defaultMedia, workspaceName, wspClient);
    }
    
    KBaseId kid = KBaseIOUtils.saveData(outputName, KBaseType.FBAModel.value(), fbaModel, workspaceName, wspClient);
//    String ref = KBaseIOUtils.saveDataSafe(outputName, KBaseType.FBAModel, fbaModel, workspaceName, dfuClient);
    
//    String uuString = UUID.randomUUID().toString();
    
    KBaseIOUtils.writeStringFile(KBaseIOUtils.toJson(kir), "/kb/module/data/data.json");
    
    List<WorkspaceObject> wsObjects = new ArrayList<> ();
    wsObjects.add(new WorkspaceObject().withDescription("model").withRef(kid.reference));
    
    if (mediaKid != null) {
      wsObjects.add(new WorkspaceObject().withDescription("media").withRef(mediaKid.reference));
    }
    
    KBaseHtmlReport htmlReport = new KBaseHtmlReport(scratch);
    List<String> files = new ArrayList<> ();
    files.add("index.html");
    List<String> datas = new ArrayList<> ();
    for (String f : files) {
      datas.add(KBaseIOUtils.getDataWeb("http://darwin.di.uminho.pt/fliu/model-report/" + f));
    }
    
    ReportFiles reportFiles = htmlReport.makeStaticReport(files, datas);
    
    File f = new File("/kb/module/data/data.json");
    if (f.exists()) {
      logger.info("copy {} -> {}", f.getAbsolutePath(), reportFiles.baseFolder);
      KBaseIOUtils.copy(f.getAbsolutePath(), reportFiles.baseFolder + "/");
    }
    
    if (kbrClient != null) {
      KBaseReporter reporter = new KBaseReporter(kbrClient, workspaceName);
      reporter.addWsObjects(wsObjects);
      reporter.addHtmlFolderShock("importer report", "index.html", reportFiles.baseFolder, dfuClient);
      final ReportInfo reportInfo = reporter.extendedReport();
      
//      final ReportInfo reportInfo = kbrClient.create(
//          new CreateParams().withWorkspaceName(workspaceName)
//                            .withReport(new Report()
//                                .withObjectsCreated(wsObjects)
//                                .withTextMessage(String.format("%s\n%s", params, geneData))));
      
      SbmlImporterResults returnVal = new SbmlImporterResults().withFbamodelId(outputName)
                                                               .withReportName(reportInfo.getName())
                                                               .withReportRef(reportInfo.getRef());
      
      return returnVal;
    }
    
    return null;
  }
}

