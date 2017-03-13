package se.lnu.prosses.securityMonitor;

import java.io.File;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.IMethodBinding;

public class JavaProjectSTSExtractor {
	JavaProjectHelper javaProjectHelper;
	Stack<String> returnTarget;
	Stack<Hashtable<SimpleName, String>> renamingRuleSets;
	Stack<Integer> scopeIdStack;
	int scopeCounter = 0;
	public Stack<String> thisContext;
	String targetPath;
	int newLocation = 1;
	public ArrayList<String> includingFilter;
	public ArrayList<String> excludingFilter;
	public STSHelper stsHelper;
	private Hashtable<String, Integer> localVariableDeclarationScopes;
	public JavaProjectSTSExtractor(JavaProjectHelper javaProjectHelper, ArrayList<String> includingFilter, ArrayList<String> excludingFilter, String targetPath) {
		this.javaProjectHelper = javaProjectHelper;
		this.targetPath = targetPath;
		this.includingFilter = includingFilter;
		this.excludingFilter = excludingFilter;
		this.returnTarget = new Stack<>();
		this.renamingRuleSets = new Stack<>();
		this.renamingRuleSets.push(new Hashtable<>());
		this.thisContext = new Stack<>();
		this.thisContext.push("");
		this.scopeIdStack = new Stack<>();
		scopeIdStack.push(0);
		this.localVariableDeclarationScopes = new Hashtable<>();
		this.stsHelper = new STSHelper();
		stsHelper.addTransition(0, 0, STS.TAU, "true", "");
	}
	
	public STSHelper extract() throws Exception{
		Utils.log(JavaProjectSTSExtractor.class, "STS extraction starts.");
		for (JavaFileHelper javaFileHelper : javaProjectHelper.getAllJavaFileHelpers()) {
			JavaClassSTSExtractor javaClassSTSExtractor = new JavaClassSTSExtractor(javaFileHelper, this);
			javaClassSTSExtractor.extract();
		}
		javaProjectHelper.recoverOriginalJavaFiles();
		Utils.log(JavaProjectSTSExtractor.class, "All original java files were recovered.");
		stsHelper.saveAsDot(targetPath + File.separator + "sts.dot");
		Utils.log(JavaProjectSTSExtractor.class, "STS graph was saved in \"" + targetPath + File.separator + "sts.dot" + "\"");
		Utils.log(JavaProjectSTSExtractor.class, "STS extraction is done.");
		return stsHelper;
	}
	
	public String getType(Name name) {
		IVariableBinding resolveBinding = (IVariableBinding)name.resolveBinding();
		ITypeBinding iTypeBinding = resolveBinding.getVariableDeclaration().getType();
		String type = "";
		if(iTypeBinding.getQualifiedName().equals("boolean")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Boolean")){
			type = "b";
		} else if(iTypeBinding.getQualifiedName().equals("int")
				|| iTypeBinding.getQualifiedName().equals("long")
				|| iTypeBinding.getQualifiedName().equals("byte")
				|| iTypeBinding.getQualifiedName().equals("short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Integer")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Long")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Byte")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Short")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigInteger")){
			type = "i";
		} else if(iTypeBinding.getQualifiedName().equals("float")
				|| iTypeBinding.getQualifiedName().equals("double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Float")
				|| iTypeBinding.getQualifiedName().equals("java.lang.Double")
				|| iTypeBinding.getQualifiedName().equals("java.lang.BigDecimal")){
			type = "r";
		} else{
			type = "u";
		}
		return type;
	}
	
	public String getUniqueName(Name name){
		String uniqueName = "";
		String declaringName = "";
		if(name.resolveBinding() instanceof IMethodBinding){
			IMethodBinding resolveBinding = (IMethodBinding)name.resolveBinding();
			declaringName = resolveBinding.getDeclaringClass().getQualifiedName() + "." + name.toString();
			uniqueName = declaringName.replaceAll("\\.", "_");
		}else if(name.resolveBinding() instanceof IVariableBinding){
			SimpleName baseQualifier = (SimpleName) getBaseQualifier(name);
			IVariableBinding resolveBinding = (IVariableBinding)baseQualifier.resolveBinding();
			uniqueName = getType(name);
			if(resolveBinding.isField()){
				if(Modifier.isStatic(resolveBinding.getModifiers())){
					declaringName = resolveBinding.getDeclaringClass().getQualifiedName() + "." + name.toString();
					uniqueName += declaringName.replaceAll("\\.", "_");
					stsHelper.setJavaName(uniqueName, declaringName);
					stsHelper.setJavaType(uniqueName, resolveBinding.getType().getQualifiedName());
					stsHelper.setJavaScope(uniqueName, STSHelper.STATICFIELD);
				}else{
					declaringName = thisContext.peek() + name.toString();
					uniqueName += declaringName.replaceAll("\\.", "_");
					stsHelper.setJavaName(uniqueName, name.toString());
					stsHelper.setJavaType(uniqueName, resolveBinding.getType().getQualifiedName());
					stsHelper.setJavaScope(uniqueName, STSHelper.INSTANCEFIELD);
				}
			}/*else if(resolveBinding.isParameter()){
				declaringName = scopeIdStack.peek() + name.toString();
				uniqueName += declaringName.replaceAll("\\.", "_");
				stsHelper.uniqueNameJavaNameMap.put(uniqueName, name.toString());
			}*/else{
				String declaringMethodName = resolveBinding.getDeclaringMethod().getDeclaringClass().getQualifiedName() 
						+ "." + resolveBinding.getDeclaringMethod().getName();
				declaringName = localVariableDeclarationScopes.get(resolveBinding.getKey()) + name.toString();
				uniqueName += declaringName.replaceAll("\\.", "_");
				if(resolveBinding.isParameter()){
					stsHelper.setJavaScope(uniqueName, STSHelper.PARAMETER);
				}else{
					stsHelper.setJavaScope(uniqueName, STSHelper.LOCAL);
				}
				stsHelper.setJavaName(uniqueName, name.toString());
				stsHelper.setJavaType(uniqueName, resolveBinding.getType().getQualifiedName());
				stsHelper.setDeclaringMethodName(uniqueName, declaringMethodName);
			}
		}
    	return uniqueName;
    }

	private Name getBaseQualifier(Name name) {
		Name baseQualifier = null;
		if(name instanceof SimpleName) {
			baseQualifier = name;
		}else{
			baseQualifier = ((QualifiedName)name).getQualifier();
			while (!(baseQualifier instanceof SimpleName)) {
				baseQualifier = ((QualifiedName)name).getQualifier();				
			}
		}
		return baseQualifier;
	}
	
	public void enterScope(){
    	scopeIdStack.push(scopeCounter++);
    }
	
    public void exitScope(){
    	scopeIdStack.pop();
    }
	
	public String rename(Expression expression){
		String renamed = "";
		if(expression instanceof BooleanLiteral || expression instanceof NumberLiteral 
				|| expression instanceof StringLiteral || expression instanceof  CharacterLiteral
				|| expression instanceof  NullLiteral){
			renamed = expression.toString();
		}else{
			renamed = expression.toString();
			renamed = " " + renamed.replaceAll("\\s*\\.\\s*", ".").replaceAll("\\s*\\(", "( ") + " ";
			Hashtable<SimpleName, String> renamingRuleSet = renamingRuleSets.peek();
			String regex = "[^\\.\\w_\\$][a-zA-Z_\\$][\\.\\w_\\$]*[^\\.]";
			Pattern pattern = Pattern.compile(regex);
	        Matcher matcher = pattern.matcher(renamed);
	        StringBuffer stringBuffer = new StringBuffer();
	        while (matcher.find()) {
	        	String find = matcher.group();
	        	String replacement = find.substring(0, 1);
	        	Name name = getNameByName(expression, find.substring(1, find.length()-1));
	        	if(find.charAt(find.length()-1)=='('){
	        		replacement = getUniqueName(name);
	        	}else{
	            	if(renamingRuleSet.containsKey(name)){
	            		replacement += renamingRuleSet.get(name);
	            	}else{
	            		replacement += getUniqueName(name);
	            	}
	        	}
	        	replacement += find.substring(find.length()-1);
				matcher.appendReplacement(stringBuffer, replacement);
	        }
	        matcher.appendTail(stringBuffer);
	        renamed = stringBuffer.toString();
		}
		return renamed;
	}
	
	static Name sname = null;
	public Name getNameByName(Expression expression, String name){
		sname = null;
		expression.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				boolean ret = true;
				if((node instanceof SimpleName && ((SimpleName)node).getIdentifier().equals(name.replaceAll("\\s", "")))
						|| (node instanceof QualifiedName && ((QualifiedName)node).toString().equals(name.replaceAll("\\s", "")))){
					sname = (Name)node;
					ret = false;
				}
				return ret;
			}
		});
		return sname;
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

	@SuppressWarnings("rawtypes")
	public boolean isEntryPoint(MethodDeclaration methodDeclaration) {
		List statements = methodDeclaration.getBody().statements();
		return (statements.size()>=1 && statements.get(0).toString().contains("se.lnu.DummyMethods.entryPoint"))
				||(statements.size()>=2 
				&& (statements.get(0).toString().contains("se.lnu.DummyMethods.entryPoint")
						||statements.get(1).toString().contains("se.lnu.DummyMethods.entryPoint")));
	}

	public String getLPCUniqueName() {
		return "LPC" + scopeIdStack.peek();
	}

	public void saveLocalVariableDeclarationScope(SimpleName variableName) {
		localVariableDeclarationScopes.put(variableName.resolveBinding().getKey(), scopeIdStack.peek());	
	}

	public void revertToLastScope() {
		scopeCounter--;
		scopeIdStack.pop();		
	}
}