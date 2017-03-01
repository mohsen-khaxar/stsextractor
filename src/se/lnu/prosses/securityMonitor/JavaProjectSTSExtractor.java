package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.SimpleName;

public class JavaProjectSTSExtractor {
	JavaProjectHelper javaProjectHelper;
	ReaxHelper reaxHelper;
	Stack<String> returnTarget;
	Stack<Hashtable<SimpleName, String>> renamingRuleSets;
	String sourcePath;
	String targetPath;
	String[] classPath;
	int newLocation = 2;
	int pcLevel = 0;
	int maxPcLevel = 0;
	Hashtable<Integer, Integer> blocks;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public ArrayList<String> entryPoints;
	public STSHelper stsHelper;
	public int scopeId = 0;
	public JavaProjectSTSExtractor(String sourcePath, String[] classPath, ArrayList<String> includingFilter, ArrayList<String> excludingFilter, String targetPath) {
		blocks = new Hashtable<>();
		this.sourcePath = sourcePath;
		this.classPath = classPath;
		this.targetPath = targetPath;
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		reaxHelper = new ReaxHelper(stsHelper);
		stsHelper.addTransition(0, 1, STS.START, "true", "");
		stsHelper.addTransition(2, 2, STS.TAU, "true", "");
	}
	
	public STSHelper extract() throws Exception{
		javaProjectHelper = new JavaProjectHelper(sourcePath, classPath);
		javaProjectHelper.load();
		for (JavaFileHelper javaFileHelper : javaProjectHelper.getAllJavaFileHelpers()) {
			JavaClassSTSExtractor javaClassSTSExtractor = new JavaClassSTSExtractor(javaFileHelper, this);
			javaClassSTSExtractor.extract();
		}
		javaProjectHelper.recoverOriginalJavaFiles();
		stsHelper.saveAsDot(targetPath + File.separator + "sts.dot");
		System.out.println("DONE.");
		return stsHelper;
	}
	
	public void addVariable(SimpleName simpleName, String renamed) {
		if(renamed.contains("resx")){
			System.out.println(renamed);
		}
		IVariableBinding resolveBinding = (IVariableBinding)simpleName.resolveBinding();
		ITypeBinding iTypeBinding = resolveBinding.getVariableDeclaration().getType();
		if(iTypeBinding.getQualifiedName().equals("boolean")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Boolean")){
			sts.variables.add("bool,"+renamed);
		} else if(iTypeBinding.getQualifiedName().equals("int")
				|| iTypeBinding.getQualifiedName().equals("long")
				|| iTypeBinding.getQualifiedName().equals("byte")
				|| iTypeBinding.getQualifiedName().equals("short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Integer")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Long")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Byte")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigInteger")){
			sts.variables.add("int,"+renamed);
		} else if(iTypeBinding.getQualifiedName().equals("float")
				|| iTypeBinding.getQualifiedName().equals("double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Float")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigDecimal")){
			sts.variables.add("real,"+renamed);
		} else{
			sts.variables.add("undef,"+renamed);
		}
		sts.variables.add("bool,LIC_" + renamed);
		sts.variables.add("bool,LII_" + renamed);
		sts.variables.add("bool,LXC_" + renamed);
		sts.variables.add("bool,LXI_" + renamed);
	}
	
	public String getUniqueName(SimpleName simpleName){
    	return "";
    }
    
    public int enterScope(){
    	int oldScopeId = this.scopeId;
    	this.scopeId++;
    	return oldScopeId;
    }
	
    public void exitScope(int oldScopeId){
    	this.scopeId = oldScopeId;
    }
	
	public String rename(Expression expression){
		String renamed = expression.toString();
		renamed = " " + renamed.replaceAll("\\.\\s*", ".").replaceFirst("[\\w_\\.\\$]+\\s*\\(", "(") + " ";
		Hashtable<SimpleName, String> renamingRuleSet = renamingRuleSets.peek();
		String regex = "[^\\.\\w_\\$][\\w_\\$]+";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(renamed);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String find = matcher.group();
        	SimpleName simpleName = getSimpleNameByName(expression, find.substring(1));
        	String replacement = find.substring(0, 0);
        	if(renamingRuleSet.containsKey(simpleName)){
        		replacement += renamingRuleSet.get(simpleName);
        	}else{
        		replacement += getUniqueName(simpleName);
        	}
			matcher.appendReplacement(stringBuffer, replacement);
        }
        matcher.appendTail(stringBuffer);
        renamed = stringBuffer.toString();
        
        regex = "[^\\.\\w_\\$][\\.\\w_\\$]+";
		pattern = Pattern.compile(regex);
        matcher = pattern.matcher(renamed);
        stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String find = matcher.group();
        	String replacement = find.replaceAll("\\.", "_");
			matcher.appendReplacement(stringBuffer, replacement);
        }
        matcher.appendTail(stringBuffer);
        renamed = stringBuffer.toString();
		return renamed;
	}
	
	static SimpleName simpleName = null;
	public SimpleName getSimpleNameByName(Expression expression, String name){
		expression.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
//				TODO must be sure that it is the root.
				if(node.getIdentifier().equals(name)){
					simpleName = node;
				}
				return false;
			}
		});
		return simpleName;
	}
	
	public Integer newLocation(){
		return ++newLocation;
	}
	
	public boolean canProcess(String methodFullName) {
		boolean res = false;
		for (String filter : includingFilter) {
			if(methodFullName.matches(filter)){
				res = true;
				break;
			}
		}
		for (String filter : excludingFilter) {
			if(methodFullName.matches(filter)){
				res = false;
				break;
			}
		}
		return res;
	}

	public boolean isEntryPoint(String qualifiedMethodName) {
		boolean isIt = false;
		for (String filter : entryPoints) {
			if (qualifiedMethodName.matches(filter)) {
				isIt = true;
				break;
			}
		}
		return isIt;
	}

	public String getLPCUniqueName() {
		return "LPC" + scopeId;
	}
}