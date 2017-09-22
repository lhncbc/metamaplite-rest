package gov.nih.nlm.nls.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Optional;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Enumeration;
import java.util.Map;  
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.FormParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Context;

import javax.servlet.ServletContext;
// import javax.servlet.ServletContextEvent;
// import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gov.nih.nlm.nls.ner.MetaMapLite;
import gov.nih.nlm.nls.metamap.document.FreeText;
import gov.nih.nlm.nls.metamap.lite.types.Entity;
import gov.nih.nlm.nls.metamap.lite.types.Ev;
import gov.nih.nlm.nls.utils.StringUtils;

import gov.nih.nlm.nls.metamap.lite.resultformats.ResultFormatter;
import gov.nih.nlm.nls.metamap.lite.resultformats.ResultFormatterRegistry;
import gov.nih.nlm.nls.metamap.document.BioCDocumentLoader;
import gov.nih.nlm.nls.metamap.document.BioCDocumentLoaderRegistry;

import com.hubspot.jinjava.Jinjava;
import bioc.BioCDocument;
import bioc.BioCPassage;

import gov.nih.nlm.nls.rest.EvScoreComparator;
  
@Path("/")
public class MetaMapLiteService
{
  @Context
  private ServletContext context;
  /** log4j logger instance */
  private static final Logger logger = LogManager.getLogger(MetaMapLiteService.class);

  Properties properties;
  // rootPath, dataPath and configPath are instantiated by InteractiveWebAppConfig class.
  /** MetaMapLite instance set from servlet context */ 
  MetaMapLite metaMapLiteInst;
  /** Jinja template renderer*/
  Jinjava jinjava = new Jinjava();



  /* Gleaned from 
     http://stackoverflow.com/questions/2999376/whats-the-right-way-of-configuring-the-path-to-a-sqlite-database-for-my-servlet
  */
  public MetaMapLiteService() {
    
  }
  
  @Context
  public void setServletContext(ServletContext context) {
    logger.info("servlet context set here");
    this.context = context;
    String rootPath = this.context.getRealPath("/");
    MetaMapLite instance = MetaMapLiteFactory.newInstance(rootPath);
    this.context.setAttribute("MetaMapLiteInstance", instance);
    Properties properties = MetaMapLiteFactory.getMetaMapProperties(rootPath);
    this.context.setAttribute("MetaMapLiteProperties", properties);
    Comparator evComparator = new EvScoreComparator();
    this.context.setAttribute("evcomparator", evComparator);
    UMLSSemanticTypes umlsSemanticTypes =
      new UMLSSemanticTypes(rootPath + "/data/SemanticTypes_2013AA.txt");
    this.context.setAttribute("semtypeinst", umlsSemanticTypes);
  }

  String loadTemplate(String filename) {
    StringBuilder sb = new StringBuilder();
    try {
      BufferedReader br = new BufferedReader(new FileReader(filename));
      String line;
      while ((line = br.readLine()) != null) {
	sb.append(line).append("\n");
      }
      br.close();
    } catch (FileNotFoundException fnfe) {
      throw new RuntimeException(fnfe);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
    return sb.toString();
  }

  @GET @Path("/")
  public String frontPage(@Context HttpServletRequest hsr)
  {
    MetaMapLite metaMapLiteInst = (MetaMapLite)this.context.getAttribute("MetaMapLiteInstance");
    if (metaMapLiteInst == null) {
      // if metaMapLiteInst is not present instantiate a new MetaMapLite instance
      String rootPath = this.context.getRealPath("/");
      MetaMapLite instance = MetaMapLiteFactory.newInstance(rootPath);
      this.context.setAttribute("MetaMapLiteInstance", instance);
    }
    UMLSSemanticTypes umlsSemanticTypes =
      (UMLSSemanticTypes)this.context.getAttribute("semtypeinst");
    Map<String, Object> jcontext = new HashMap();
    String rootPath = this.context.getRealPath("/");
    String servletPath = hsr.getServletPath();
    String contextPath = hsr.getContextPath();
    String pathInfo = hsr.getPathInfo();
    String formTemplate = loadTemplate(rootPath + "/templates/form.html");
    jcontext.put("action", contextPath + servletPath + "/annotate");
    jcontext.put("dflist", BioCDocumentLoaderRegistry.listNameSet());
    jcontext.put("rflist", ResultFormatterRegistry.listNameSet());
    // jcontext.put("sourcelist", UMLSSources.getSourceList());
    jcontext.put("semtypelist", umlsSemanticTypes.getSemanticTypeList());
    String form = this.jinjava.render(formTemplate, jcontext);
    Map<String, Object> fcontext = new HashMap();
    fcontext.put("contextpath", contextPath );
    fcontext.put("servletpath", servletPath);
    fcontext.put("urlpath", contextPath + servletPath);
    fcontext.put("form", form);
    fcontext.put("title", "Interactive MetaMapLite");
    String template = loadTemplate(rootPath + "/templates/frontpage.html");
    return this.jinjava.render(template, fcontext);
  }

  @GET @Path("/context")
  public String processContext(@Context HttpServletRequest hsr) {
    String rootPath = this.context.getRealPath("/");
    String template = loadTemplate(rootPath + "/templates/context.html");
      // jcontext.put("contextlist", this.context.;
    List<String> descriptionList = new ArrayList<String>();
    descriptionList.add("hsr.getContextPath() -> " + hsr.getContextPath());
    descriptionList.add("hsr.getPathInfo() -> " + hsr.getPathInfo());
    descriptionList.add("hsr.getServletPath() -> " + hsr.getServletPath());
    // don't display this on deployed versions of the app
    // descriptionList.add("this.context.getRealPath(\"/\") -> " + this.context.getRealPath("/"));
    for (String entry: descriptionList) {
      System.out.println(entry);
    }

    List<String> headerlist = new ArrayList<String>();
    for (Enumeration e = hsr.getHeaderNames(); e.hasMoreElements();) {
      String key = (String)e.nextElement();
      headerlist.add(key + " -> " + hsr.getHeader(key));
    }

    Map<String, Object> jcontext = new HashMap();
    jcontext.put("title", "Context Information");
    jcontext.put("headerlist", headerlist);
    jcontext.put("descriptionlist", descriptionList);
    return this.jinjava.render(template, jcontext);
  }
  
  @GET @Path("/ctxt")
  public String processCtxt() {
    return "context";
  }

  @GET @Path("/options")
  public String getOptionList()
  {
    return "list options (TBD)";
  }

  @GET @Path("/datasets")
  public String getDataSetList()
  {
    return "list data sets (TBD)";
  }

  @GET @Path("/sources")
  public String getSourceList()
  {
    return "list sources (TBD)";
  }

  @GET @Path("/semantictypes")
  public String getSemTypeList()
  {
    return "list semantic types (TBD)";
  }

  @GET @Path("/requestcontenttypes")
  public String getRequestContentTypeList()
  {
    return "list supported request content types (TBD)";
  }

  @GET @Path("/responsecontenttypes")
  public String getResponseContentTypeList()
  {
    return "list supported response content types (TBD)";
  }

  @GET @Path("/annotate")
  public String processAnnotateGet()
  {
    return "Error: this server only accepts POST requests for /annotate.";
  }

  /**
   * annotate inputtext return result as text/html
   * @param inputText text to be annotated 
   * @param resultFormat format result using supplied format or html if no format supplied.
   * @param sourcesStringList restrict results to supplied sources or use all sources if empty or "all"
   * @param semanticTypeStringList restrict results to supplied semantic
   * types or use all sources if empty or "all"
   * @return entityList
   */
  public List<Entity> processText(String inputText,
				  String docFormat,
				  String resultFormat,
				  List<String> sourcesStringList,
				  List<String> semanticTypesStringList)
    throws Exception
  {
    // use servlet context to set various variables
    MetaMapLite metaMapLiteInst = (MetaMapLite)this.context.getAttribute("MetaMapLiteInstance");
    String[] sourceList;
    if (sourcesStringList != null) {
      if (sourcesStringList.size() == 0) {
	sourceList = new String[1];
	sourceList[0] = "all";
	sourcesStringList.remove(0);
	sourcesStringList.add("all");
      } else if (sourcesStringList.size() == 1) {
	String el = sourcesStringList.get(0);
	if (el.trim().length() == 0) {
	  sourceList = new String[1];
	  sourceList[0] = "all";
	  sourcesStringList.remove(0);
	  sourcesStringList.add("all");
	} else {
	  sourceList = el.split(",");
	}
      } else {
	sourceList = sourcesStringList.toArray(new String[0]);
      }
      logger.info("sourceList=" + Arrays.toString(sourceList));
      metaMapLiteInst.setSourceSet(sourceList);
    } 
    if (semanticTypesStringList != null) {
      if (semanticTypesStringList.size() == 0) {
	semanticTypesStringList.add("all");
      }
      String[] semanticTypeList = semanticTypesStringList.toArray(new String[0]);
      metaMapLiteInst.setSemanticGroup(semanticTypeList);
      logger.info("semanticTypeList=" + semanticTypeList);
    }

    String documentFormat = "freetext";
    if (docFormat != null) {
      documentFormat = docFormat;
    }
    BioCDocumentLoader docLoader;
    if (BioCDocumentLoaderRegistry.contains(documentFormat)) {
      docLoader = BioCDocumentLoaderRegistry.get(documentFormat);
    } else {
      docLoader = new FreeText();
    }
    List<BioCDocument> documentList = docLoader.readAsBioCDocumentList(new StringReader(inputText));
    List<Entity> entityList = metaMapLiteInst.processDocumentList(documentList);
    return entityList;
  }

  /**
   * annotate inputtext return result as text/html
   * @param inputText text to be annotated 
   * @param resultFormat format result using supplied format or html if no format supplied.
   * @param sourcesString restrict results to supplied sources or use all sources if empty or "all"
   * @param semanticTypeString restrict results to supplied semantic
   * types or use all sources if empty or "all"
   * @return response as html
   */
  public String processAnnotateHtml(String inputText,
				    String docFormat,
				    String resultFormat,
				    List<String> sourcesStringList,
				    List<String> semanticTypesStringList)
  {
    // use servlet context to set various variables
    String rootPath = this.context.getRealPath("/");
    Properties properties = (Properties)this.context.getAttribute("MetaMapLiteProperties");
    EvScoreComparator evComparator = (EvScoreComparator)this.context.getAttribute("evcomparator");
    try {
      if (inputText == null) {
	return "Error: Required field inputtext not supplied.";
      }
      List<Entity> entityList = processText(inputText, docFormat, resultFormat,
					    sourcesStringList, semanticTypesStringList);
      logger.info("resultFormat=" + resultFormat);
      logger.info("sourcesString=" + sourcesStringList);
      logger.info("semanticTypesString=" + semanticTypesStringList);
      if (resultFormat.length() == 0) {
	resultFormat = "html";
      }
      StringBuilder sb = new StringBuilder();
      if (resultFormat.equals("html")) {
	String template = loadTemplate(rootPath + "/templates/result_html.html");
	Map<String, Object> jcontext = new HashMap();
	jcontext.put("inputtext", inputText);
	jcontext.put("entitylistsize", Integer.toString(entityList.size()));
	Set<Ev> evSet = new HashSet<Ev>();
	for (Entity entity: entityList) {
	  for (Ev ev: entity.getEvSet()) {
	    evSet.add(ev);
	  }
	}
	Ev[] evArray = evSet.toArray(new Ev[0]);
	Arrays.sort(evArray, evComparator);
	jcontext.put("title", "Results");
	jcontext.put("evlist", evArray);
	jcontext.put("sourceslist", Arrays.toString(sourcesStringList.toArray()));
	jcontext.put("semtypelist", Arrays.toString(semanticTypesStringList.toArray()));
	sb.append(this.jinjava.render(template, jcontext));
      } else if (ResultFormatterRegistry.get(resultFormat) != null) {
      	ResultFormatter formatter = ResultFormatterRegistry.get(resultFormat);
      	if (formatter != null) {
	  
	  // logger.info("properties: ");
	  // for (Map.Entry<Object,Object> entry: properties.entrySet()) {
	  //   logger.info(" " + entry.getKey() + "=" + entry.getValue());
	  // }

      	  formatter.initProperties(properties);
	  String template = loadTemplate(rootPath + "/templates/result_preformatted.html");
	  Map<String, Object> jcontext = new HashMap();
	  jcontext.put("title", "Results");
	  jcontext.put("inputtext", inputText);
	  jcontext.put("entitylistsize", Integer.toString(entityList.size()));
	  jcontext.put("result", formatter.entityListFormatToString(entityList));
	  jcontext.put("sourceslist", Arrays.toString(sourcesStringList.toArray()));
	  jcontext.put("semtypelist", Arrays.toString(semanticTypesStringList.toArray()));
      	  sb.append(this.jinjava.render(template, jcontext));
      	} else {
      	  sb.append("! Couldn't find formatter for output format option: " + resultFormat);
      	}
      } else {
	sb.append("Result Format ").append(resultFormat).append(" is not available.\n");
      }
      return sb.toString();
      // return "annotate tbi-urlencoded: inputtext: " + inputText +
      //   ", resultformat: " + resultFormat +
      //   ", sources: " + sourcesString +
      //   ", semtypes: " + semanticTypesString;
    } catch (Exception e) {
      logger.error("Internal server exception: ", e);
      return "Internal server error, contact administrator.";
    }
  }

  public String processAnnotateJson(String inputText, String docFormat, String resultFormat,
				    List<String> sourcesString, List<String> semanticTypesString)
  {
    try {
      List<Entity> entityList = processText(inputText, docFormat, resultFormat,
					    sourcesString, semanticTypesString);
      StringBuilder sb = new StringBuilder();
      if (ResultFormatterRegistry.get(resultFormat) != null) {
	ResultFormatter formatter = ResultFormatterRegistry.get(resultFormat);
	if (formatter != null) {
	  formatter.initProperties(properties);
	  sb.append(formatter.entityListFormatToString(entityList));
	} else {
	  sb.append("! Couldn't find formatter for output format option: ").append(resultFormat);
	}
      } else {
	sb.append("Result Format ").append(resultFormat).append(" is not available.\n");
      }
      return sb.toString();
    } catch (Exception e) {
      logger.error("Internal server exception: ", e);
      return "Internal server error, contact administrator.";
    }
  }

  public String processAnnotateXml(String inputText, String docFormat, String resultFormat,
				   List<String> sourcesString, List<String> semanticTypesString)
  {
    try {
      List<Entity> entityList = processText(inputText, docFormat, resultFormat,
					    sourcesString, semanticTypesString);
      StringBuilder sb = new StringBuilder();
      if (ResultFormatterRegistry.get(resultFormat) != null) {
	ResultFormatter formatter = ResultFormatterRegistry.get(resultFormat);
	if (formatter != null) {
	  formatter.initProperties(properties);
	  sb.append(formatter.entityListFormatToString(entityList));
	} else {
	  sb.append("! Couldn't find formatter for output format option: ").append(resultFormat);
	}
      } else {
	sb.append("Result Format ").append(resultFormat).append(" is not available.\n");
      }
      return sb.toString();
    } catch (Exception e) {
      logger.error("Internal server exception: ", e);
      return "Internal server error, contact administrator.";
    }
  }

  public String processAnnotatePlain(String inputText, String docFormat, String resultFormat,
				     List<String> sourcesString, List<String> semanticTypesString)
  {
    try {
      List<Entity> entityList = processText(inputText, docFormat, resultFormat,
					    sourcesString, semanticTypesString);
      StringBuilder sb = new StringBuilder();
      if (ResultFormatterRegistry.get(resultFormat) != null) {
	ResultFormatter formatter = ResultFormatterRegistry.get(resultFormat);
	if (formatter != null) {
	  formatter.initProperties(properties);
	  sb.append(formatter.entityListFormatToString(entityList));
	} else {
	  sb.append("! Couldn't find formatter for output format option: ").append(resultFormat);
	}
      } else {
	sb.append("Result Format ").append(resultFormat).append(" is not available.\n");
      }
      return sb.toString();
    } catch (Exception e) {
      logger.error("Internal server exception: ", e);
      return "Internal server error, contact administrator.";
    }
  }


  @POST @Path("/annotate")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public String processAnnotateFormInput
    (@FormParam("inputtext") String inputText,
     @DefaultValue("freetext") @FormParam("docformat") String docFormat,
     @DefaultValue("html") @FormParam("resultformat") String resultFormat,
     @DefaultValue("all") @FormParam("sourcesString") List<String> sourcesString,
     @DefaultValue("all") @FormParam("semanticTypeString") List<String> semanticTypesString)
  {

    logger.info("multipart form data: request.inputext:  " + inputText);
    return processAnnotateHtml(inputText,
			       docFormat,
			       resultFormat,
			       sourcesString,
			       semanticTypesString);
  }


  @POST @Path("/annotate")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public String processAnnotateUrlEncodedFormInput
    (@FormParam("inputtext") String inputText,
     @DefaultValue("freetext") @FormParam("docformat") String docFormat,
     @DefaultValue("html") @FormParam("resultformat") String resultFormat,
     @DefaultValue("all") @FormParam("sourcesString") List<String> sourcesString,
     @DefaultValue("all") @FormParam("semanticTypeString") List<String> semanticTypesString)
  {
    logger.info("www form urlencoded: request.inputext:  " + inputText);
    return processAnnotateHtml(inputText,
			       docFormat,
			       resultFormat,
			       sourcesString,
			       semanticTypesString);
  }


  //  @DefaultValue("brat") @FormParam("outputformat") String outputFormat,
  @POST @Path("/annotate/json")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public String processAnnotateJsonInput(MMRequest request)
  {
    logger.info("JSON: request.inputext:  " + request.getInputtext());
    List<String> semtypeList = new ArrayList<String>();
    semtypeList.add("all");
    List<String> sourceList = new ArrayList<String>();
    sourceList.add("all");
    return processAnnotateJson(request.getInputtext(),
			       "freetext" /*docformat*/,
			       "brat" /*resultFormat*/,
			       sourceList /*sourcesStringList*/,
			       semtypeList /*semanticTypesStringList*/);
    
  }

  //  @DefaultValue("brat") @FormParam("outputformat") String outputFormat,
  @POST @Path("/annotate/text")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_PLAIN)
  public String processAnnotateJsonInputToText(MMRequest request)
  {
    logger.info("JSON: request.inputext:  " + request.getInputtext());
    List<String> semtypeList = new ArrayList<String>();
    semtypeList.add("all");
    List<String> sourceList = new ArrayList<String>();
    sourceList.add("all");
    return processAnnotateJson(request.getInputtext(),
			       "freetext" /*docformat*/,
			       "brat" /*resultFormat*/,
			       sourceList /*sourcesStringList*/,
			       semtypeList /*semanticTypesStringList*/);
    
  }

  
  @POST @Path("/annotate")
  @Consumes(MediaType.TEXT_XML)
  public String processAnnotateXMLInput(MMRequest request)
  {
    logger.info("XML: request.inputext:  " + request.getInputtext());
    List<String> semtypeList = new ArrayList<String>();
    semtypeList.add("all");
    List<String> sourceList = new ArrayList<String>();
    sourceList.add("all");
    return processAnnotateJson(request.getInputtext(),
			       "freetext" /*docformat*/,
			       "brat" /*resultFormat*/,
			       sourceList /*sourcesStringList*/,
			       semtypeList /*semanticTypesStringList*/);
    
  }


  
  //  @DefaultValue("brat") @FormParam("outputformat") String outputFormat,
  @POST @Path("/annotate/text")
  @Consumes(MediaType.TEXT_PLAIN)
  public String processAnnotatePlainInput(MMRequest request)
  {
    logger.info("Text/Plain: request.inputext:  " + request.getInputtext());
    List<String> semtypeList = new ArrayList<String>();
    semtypeList.add("all");
    List<String> sourceList = new ArrayList<String>();
    sourceList.add("all");
    return processAnnotateJson(request.getInputtext(),
			       "freetext" /*docformat*/,
			       "brat" /*resultFormat*/,
			       sourceList /*sourcesStringList*/,
			       semtypeList /*semanticTypesStringList*/);
  }



  @POST @Path("/annotate")
  @Consumes("application/edn")
  public String processAnnotateEdnInput()
  {
    return "annotate tbi-edn";
  }

  @POST @Path("/annotate")
  @Consumes("application/x-yaml")
  public String processAnnotateYamlInput()
  {
    return "annotate tbi-edn";
  }


  
}
