package pt.uminho.sysbio.biosynthframework.kbase;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import pt.uminho.ceb.biosystems.mew.biocomponents.container.components.GeneReactionRuleCI;
import pt.uminho.ceb.biosystems.mew.utilities.grammar.syntaxtree.AbstractSyntaxTreeNode;
import pt.uminho.ceb.biosystems.mew.utilities.math.language.mathboolean.DataTypeEnum;
import pt.uminho.ceb.biosystems.mew.utilities.math.language.mathboolean.IValue;
import pt.uminho.ceb.biosystems.mew.utilities.math.language.mathboolean.node.Variable;
import pt.uminho.ceb.biosystems.mew.utilities.math.language.mathboolean.parser.TokenMgrError;
import pt.uminho.sysbio.biosynthframework.BFunction;

public class KBaseUtils {
  
  private static final Logger logger = LoggerFactory.getLogger(KBaseUtils.class);
  
  public static Set<String> collect(AbstractSyntaxTreeNode<DataTypeEnum, IValue> root) {
    Set<String> data = new HashSet<> ();
    if (root instanceof Variable) {
      Variable var = (Variable) root;
      data.add(var.toString());
    }
    int childs = root.getNumberOfChildren();
    for (int i = 0; i < childs; i++) {
      data.addAll(collect(root.getChildAt(i)));
    }
    
    return data;
  }

  public static Set<String> getGenes(GeneReactionRuleCI ruleCI) {
//    Set<String> genes = new HashSet<> ();
    
    AbstractSyntaxTreeNode<DataTypeEnum, IValue> root = ruleCI.getRule().getRootNode();
    
    return collect(root);
  }
  
  public static Set<String> getGenes(String gpr, BFunction<String, String> geneTransform) {
    Set<String> genes = new TreeSet<> ();
    Set<String> grp = KBaseUtils.getGenes(gpr);
    
    if (grp != null && !grp.isEmpty()) {
      Set<String> validLocus = new HashSet<> ();
      for (String g : grp) {
        if (!NumberUtils.isNumber(g.trim())) {
          if (geneTransform != null) {
            g = geneTransform.apply(g);
          }
          if (g != null && !g.trim().isEmpty()) {
            validLocus.add(g);
          }
        } else {
          logger.warn("[IGNORE NUMBER LOCUS] gene: {}", g);
        }
      }
      genes.addAll(validLocus);
    }
    
    logger.trace("GPR: {}, Genes: {}", gpr, genes);
    
    return genes;
  }
  
  public static Set<String> getGenes(String gprExpression) {
    Set<String> genes = new HashSet<> ();
    if (gprExpression == null || gprExpression.trim().isEmpty()) {
      return genes;
    }
    
    if (gprExpression.contains("N/A")) {
      gprExpression = gprExpression.replaceAll("N/A", "unknown");
    }
    
    String[] tks = new String[]{"\""};
    
    String prev = gprExpression.trim();
    do {
      prev = gprExpression;
      for (String s : tks) {
        gprExpression = StringUtils.removeStart(gprExpression, s);
        gprExpression = StringUtils.removeEnd(gprExpression, s);
      }
    } while (prev != gprExpression);
    
//    @ |; | / 
    for (String subgpr : gprExpression.split("[;|]")) {
      if (subgpr != null && !subgpr.trim().isEmpty()) {
        GeneReactionRuleCI grrci;
        try {
          grrci = new GeneReactionRuleCI(subgpr);
          genes.addAll(getGenes(grrci));
        } catch (Exception | TokenMgrError e) {
          logger.warn("invalid gpr: [{}], {}", subgpr, e.getMessage());
        }
      }

    }
    return genes;
  }

  public static<T> T convert(Object object, Class<T> clazz) {
    ObjectMapper om = new ObjectMapper();
    T out = om.convertValue(object, clazz);
    return out;
  }
}
