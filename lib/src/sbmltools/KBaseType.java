package sbmltools;

public enum KBaseType {
  
  FBAModel("KBaseFBA.FBAModel"),
  Genome("KBaseGenomes.Genome"), 
  KBaseBiochemMedia("KBaseBiochem.Media");

  private final String value;
  
  KBaseType(String v) {
    value = v;
  }
  
  public String value() { return value; } 
}
