package se.lnu.prosses.securityMonitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

public class CodeTransformer {
	STSHelper stsHelper; 
	JavaProjectHelper javaProjectHelper;
	
	public CodeTransformer(STSHelper stsHelper, JavaProjectHelper javaProjectHelper) {
		this.stsHelper= stsHelper; 
		this.javaProjectHelper = javaProjectHelper;
	}
	
	public void transform() throws Exception{
		Hashtable<String, String> advices = generateAdvices();
		instrumentJavaProject(advices);
		Utils.log(CodeTransformer.class, "Instrumentation is done.");
	}

	private void instrumentJavaProject(Hashtable<String, String> advices) throws Exception {
		for (JavaFileHelper javaFileHelper : javaProjectHelper.getAllJavaFileHelpers()) {
			instrumentJavaFile(javaFileHelper, advices);
		}
	}

	private void instrumentJavaFile(JavaFileHelper javaFileHelper, Hashtable<String, String> advices) throws Exception {
		Utils.log(CodeTransformer.class, "Instrumentation starts for the java file \"" + javaFileHelper.getJavaFilePath() + "\"");
		CompilationUnit compilationUnit = javaFileHelper.getCompilationUnit();
		MethodDeclaration[] methods = ((TypeDeclaration)compilationUnit.types().get(0)).getMethods();
		for (MethodDeclaration methodDeclaration : methods) {
			instrumentMethodBody(javaFileHelper, methodDeclaration.getBody(), advices);
			Utils.log(CodeTransformer.class, "Instrumentation is done for the method \"" + javaFileHelper.getQualifiedName(methodDeclaration) + "\"");
		}
		javaFileHelper.saveModifiedJavaFile();
		Utils.log(CodeTransformer.class, "Instrumentation is done for the java file \"" + javaFileHelper.getJavaFilePath() + "\"");
	}

	private void instrumentMethodBody(JavaFileHelper javaFileHelper, Block body, Hashtable<String, String> advices) throws Exception {
		injectAdvicesInMethodBody(javaFileHelper, body, advices);
		int auxVariableIndex = 0;
		for (Object object : body.statements()) {
			auxVariableIndex = instrumentStatement(javaFileHelper, body, advices, auxVariableIndex, (Statement) object);
		}
	}

	private int instrumentStatement(JavaFileHelper javaFileHelper, Block body, Hashtable<String, String> advices, int auxVariableIndex, Statement statement) throws Exception {
		List<Block> blocks = getBlocks(statement);
		if(blocks.size()>0){
			for (Block block : blocks) {
				for (Object object : block.statements()) {
					auxVariableIndex = instrumentStatement(javaFileHelper, body, advices, auxVariableIndex, (Statement) object);
				}
			}
		}
		if(stsHelper.defaultActions.containsKey(statement.getStartPosition())){
			String code = "if((boolean)se.lnu.MonitorHelper.getLocalVariableValue(\"@mode\")){"
					+ stsHelper.defaultActions.get(statement.getStartPosition()) + ";"
					+ "}else{"
					+ statement.toString()
					+ "}";
			javaFileHelper.insertStatementInsteadOf(statement, javaFileHelper.parseStatement(code));
		}else if(!(statement instanceof Block)){
			List<Expression> methodInvocations = getMethodInvocations(statement);
			String transformedCode = statement.toString();
			boolean modified = false;
			if(statement instanceof WhileStatement || statement instanceof ForStatement 
					|| statement instanceof DoStatement || statement instanceof EnhancedForStatement){
				modified = true;
				Statement innerStatement = getInnerStatement(statement);
				String advice = advices.get("se.lnu.DummyMethods.monitorablePoint").substring(advices.get("se.lnu.DummyMethods.monitorablePoint").indexOf("@")+1);
				advice = advice.substring(0, advice.indexOf("if(violation){")) + "}";
				advice = advice.replace("boolean violation=false;", "");
				String localVariablesSection = getLocalVariablesSection(javaFileHelper, javaFileHelper.getQualifiedName(body.getParent())
						, "se.lnu.DummyMethods.monitorablePoint", advices);
				if(innerStatement instanceof Block){
					transformedCode = transformedCode.substring(0, transformedCode.lastIndexOf("}")-1)
							+ localVariablesSection
							+ advice
							+ "}"
							+ transformedCode.substring(transformedCode.lastIndexOf("}")+1);
				}else{
					transformedCode = transformedCode.replaceAll(innerStatement.toString(), "{"
							+ innerStatement.toString() + ";"
							+ localVariablesSection
							+ advice
							+ "}");
				}
			}
			for (int i = methodInvocations.size()-1; i>=0; i--) {
				String qualifiedName = javaFileHelper.getQualifiedName(methodInvocations.get(i));
				String typeQualifiedName = javaFileHelper.getExpressionType(methodInvocations.get(i)).getQualifiedName();
				if(advices.get(qualifiedName)!=null){
					modified = true;
					String localVariablesSection = getLocalVariablesSection(javaFileHelper, javaFileHelper.getQualifiedName(body.getParent())
							, qualifiedName, advices);
					if(!typeQualifiedName.equals("void")){
						String auxVariable = "__X" + auxVariableIndex;
						auxVariableIndex++;
						transformedCode = transformedCode.replaceAll(methodInvocations.get(i).toString().replaceAll("\\(", "\\\\(").replaceAll("\\)", "\\\\)"), auxVariable);
						String methodInvocationSection = typeQualifiedName + " " + auxVariable + "=" + methodInvocations.get(i).toString() + ";";
						transformedCode = localVariablesSection + "se.lnu.MonitorHelper.setLocalVariableValue(\"@isCheckPoint\", true);" 
						+ methodInvocationSection + transformedCode + "se.lnu.MonitorHelper.setLocalVariableValue(\"@isCheckPoint\", false);";
					}else{
						transformedCode = localVariablesSection + "se.lnu.MonitorHelper.setLocalVariableValue(\"@isCheckPoint\", true);" 
								+ transformedCode + "se.lnu.MonitorHelper.setLocalVariableValue(\"@isCheckPoint\", false);";
					}
				}
			}
			if(modified){
				Block block = (Block) javaFileHelper.parseStatement("{" + transformedCode + "}");
				javaFileHelper.insertStatementsInsteadOf(statement, block.statements());
			}
		}
		return auxVariableIndex;
	}
	Statement innerStatement;
	private Statement getInnerStatement(Statement statement) {
		innerStatement = null;
		statement.accept(new ASTVisitor() {
			@Override
			public boolean preVisit2(ASTNode node) {
				boolean res = true;
				if(!node.equals(statement) && node instanceof Statement){
					res = false;
					innerStatement = (Statement) node;
				}
				return res;
			}
		});
		return innerStatement;
	}

	private void injectAdvicesInMethodBody(JavaFileHelper javaFileHelper, Block body, Hashtable<String, String> advices) throws Exception {
		String methodQualifiedName = javaFileHelper.getQualifiedName(body.getParent());
		String advice = advices.get(methodQualifiedName);
		if(advice!=null){
			String adviceCode = advice.substring(advice.indexOf("@")+1);
			adviceCode = "if((boolean)se.lnu.MonitorHelper.getLocalVariableValue(\"@isCheckPoint\"))" + adviceCode;
			if(!body.statements().isEmpty()){
				javaFileHelper.insertStatementBefore((ASTNode) body.statements().get(0), javaFileHelper.parseStatement(adviceCode));
			}else{
				javaFileHelper.insertStatementInsteadOf(body, javaFileHelper.parseStatement("{" + javaFileHelper.parseStatement(adviceCode) + "}"));
			}
		}
	}

	private String getLocalVariablesSection(JavaFileHelper javaFileHelper, String callerQualifiedName, String calleeQualifiedName, Hashtable<String, String> advices) {
		String advice = advices.get(calleeQualifiedName);
		String localVariablesSection = "";
		if(advice!=null){
			String localVariableNames = advice.substring(0, advice.indexOf("@"));
			if(!localVariableNames.equals("")){
				String[] parts = localVariableNames.split(",");
				for (String localVariableName : parts) {
					if(stsHelper.getDeclaringMethodName(localVariableName).equals(callerQualifiedName)){
						localVariablesSection += "se.lnu.MonitorHelper.setLocalVariableValue(\"" 
									+ localVariableName + "\", " + stsHelper.getJavaName(localVariableName) + ");";
					}
				}
			}
		}
		return localVariablesSection;
	}

	static ArrayList<Expression> methodInvocations;
	private List<Expression> getMethodInvocations(Statement statement) {
		methodInvocations = new ArrayList<>();
		statement.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation node) {
				ASTNode parent = node.getParent();
				while(!parent.equals(statement)&&!(parent instanceof Block)){
					parent = parent.getParent();
				}
				if(parent.equals(statement)&&!isUnderBranch(node)){
					methodInvocations.add(node);
				}
				return true;
			}
			private boolean isUnderBranch(ASTNode node) {
				boolean isUnderBranch = false;
				while(!(node instanceof MethodDeclaration)){
					node = node.getParent();
					if(node instanceof WhileStatement || node instanceof IfStatement){
						isUnderBranch = true;
						break;
					}
				}
				return isUnderBranch;
			}
			@Override
			public boolean visit(ClassInstanceCreation node) {
				ASTNode parent = node.getParent();
				while(!parent.equals(statement)&&!(parent instanceof Block)){
					parent = parent.getParent();
				}
				if(parent.equals(statement)&&!isUnderBranch(node)){
					methodInvocations.add(node);
				}
				return true;
			}
		});
		return methodInvocations;
	}
	
	static ArrayList<Block> blocks;
	private List<Block> getBlocks(Statement statement) {
		blocks = new ArrayList<>();
		if(statement instanceof Block){
			blocks.add((Block) statement);
		}else{
			statement.accept(new ASTVisitor() {
				@Override
				public boolean visit(Block node) {
					if(!node.equals(statement)&&node.getParent().equals(statement)){
						blocks.add(node);
					}
					return true;
				}
			});
		}
		return blocks;
	}

	private Hashtable<String, String> generateAdvices() throws IOException {
		Utils.log(CodeTransformer.class, "Generating advices starts.");
		Hashtable<String, String> advices = new Hashtable<>();
		for (String monitorableAction : stsHelper.monitorableActions) {
			String qualifiedMonitorableMethodName = stsHelper.getQualifiedMethodName(monitorableAction);
			MethodDeclaration methodDeclaration = getMethodDeclaration(qualifiedMonitorableMethodName);
			boolean isStatic = false;
			if(!methodDeclaration.getName().toString().equals("monitorablePoint") 
					&& Modifier.isStatic(methodDeclaration.resolveBinding().getModifiers())){
				isStatic = true;
			}
			String parameters = methodDeclaration.parameters().toString().replace("[", "").replace("]", "");
			String code = "";
			code += generatePreMainPart(qualifiedMonitorableMethodName, isStatic);
			String mainPart = generateMainPart(monitorableAction, isStatic);
			code += mainPart.substring(mainPart.indexOf("@")+1, mainPart.length());
			code += generatePostMainPart(qualifiedMonitorableMethodName, parameters);
			code = mainPart.substring(0, mainPart.indexOf("@")) + "@{" + code + "}";
			advices.put(qualifiedMonitorableMethodName, code);
		}
		Utils.log(CodeTransformer.class, "Generating advices is done.");
		return advices;
	}

	private String generatePreMainPart(String qualifiedMethodName, boolean isStatic) throws IOException {
		String code = "";
		code += "Long monitorInstanceId = se.lnu.MonitorHelper.getMonitorInstanceId(";
		code += isStatic ? qualifiedMethodName + ".class" : "this";
		code += ");\n";
		code += "Integer currentLocation = se.lnu.MonitorHelper.getCurrentLocation(monitorInstanceId);\n";
		code += "\tboolean violation=false;\n";
		return code;
	}
	
	private String generateMainPart(String monitorableAction, boolean isStatic) throws IOException {
		boolean check = false;
		String code = "";
		String localVariables = "";
		String separator = "";
		for (Transition transition : stsHelper.getTransitions()) {
			if(transition.getAction().equals(monitorableAction)){
				check = true;
				Hashtable<String, Object> extraData = transition.getExtraData();
				code += "if(currentLocation==" + transition.getSource() + " && " + 
				convertToJavaSyntax(transition.getGuard()) + "){\n";
				code += getSecurityAssignments(transition.getUpdate());
				if(extraData!=null&&extraData.get("@status").equals(STS.INSECURE)){
					code += "\tviolation=true;\n";
				}
				if(stsHelper.getOutDegree(transition.getTarget())==0){
					code += "\tse.lnu.MonitorHelper.removeMonitorInstanceId(monitorInstanceId);\n";
				} else {
					code += "\tcurrentLocation = " + transition.getTarget() + ";\n";
				}
				code += "}else ";
				localVariables += separator + getLocalVariables(transition.getGuard());
				if(!localVariables.equals("")){
					separator = ",";
				}
			}
		}
		if(check){
			code += "{se.lnu.MonitorHelper.throwException(";
			code += isStatic ? stsHelper.getQualifiedMethodName(monitorableAction) + ".class" : "this";
			code += ", \"Safety Violation\");}\n";
		}
		code += "\n";
		localVariables = removeDuplicates(localVariables);
		code = localVariables + "@" + code;
		return code;
	}
	
	private String generatePostMainPart(String qualifiedControllableMethodName, String parameters)
			throws IOException {
		String code = "";
		code += "se.lnu.MonitorHelper.setCurrentLocation(monitorInstanceId, currentLocation);\n";
		code += "if(violation){\n";
		code += "\tObject[] res = se.lnu.MonitorHelper.applyCountermeasure(monitorInstanceId, \"" + qualifiedControllableMethodName + "\");\n";
		code += "\tif(((Integer)res[0])!=0){\n";
		code += "se.lnu.MonitorHelper.setLocalVariableValue(\"@mode\", false);";
		code += "\n\t}";
		code += "else{se.lnu.MonitorHelper.setLocalVariableValue(\"@mode\", true);\n}\n";
		code += "}\n";
		return code;
	}

	private String getSecurityAssignments(String update) {
		String securityAssignments = "";
		if(!update.matches("\\s*")){
			String[] assignments = update.split(";");
			for (String assignment : assignments) {
				if(assignment.matches("\\s*L.+")){
					securityAssignments += assignment + ";";
				}
			}
		}
		return "se.lnu.MonitorHelper.setSecurityLevel(\"" + securityAssignments + "\");";
	}
	
//	private String getSecurityAssignments(String update) {
//		String securityAssignments = "";
//		if(!update.matches("\\s*")){
//			String[] assignments = update.split(";");
//			for (String assignment : assignments) {
//				if(assignment.matches("\\s*L.+")){
//					assignment = assignment.replaceAll("\\sor\\s", "||").replaceAll("\\snot\\s", "!");
//					String leftHandSide = assignment.split("=")[0];
//					String rightHandSide = assignment.split("=")[1];
//					String regex = "[a-zA-Z_$][\\w_$]*"; 
//					Pattern pattern = Pattern.compile(regex);
//			        Matcher matcher = pattern.matcher(rightHandSide);
//			        StringBuffer processedCode = new StringBuffer();
//			        while (matcher.find()) {
//			        	String find = matcher.group();
//			        	String replace = ""; 
//			        	if(find.equals("true")||find.equals("false")){
//							replace = find;
//						}else{
//							replace = "se.lnu.MonitorHelper.getSecurityLevel(\"" + find + "\")";
//						}
//						matcher.appendReplacement(processedCode, replace);
//			        }
//			        matcher.appendTail(processedCode);
//			        securityAssignments += "se.lnu.MonitorHelper.setSecurityLevel(\"" + leftHandSide + "\", " + processedCode.toString() + ");";
//				}
//			}
//		}
//		return securityAssignments;
//	}

	private String removeDuplicates(String localVariables) {
		String[] parts = localVariables.split(",");
		HashSet<String> temp = new HashSet<>();
		for (String part : parts) {
			temp.add(part);
		}
		String separator = "";
		localVariables = "";
		for (String string : temp) {
			localVariables += separator + string;
			separator = ",";
		}
		return localVariables;
	}

	private String getLocalVariables(String guard) {
		guard = guard.replaceAll(" and ", " & ");
		guard = guard.replaceAll(" or ", " & ");
		guard = guard.replaceAll(" not ", " & ");
		String localVariables = "";
		String[] guardParts = guard.replaceAll("\\W\\d+\\W|(\\s*true\\s*)|(\\s*false\\s*)|\\s", "").split("\\W+");
		sort(guardParts);		
		String separator = "";
		for (String guardPart : guardParts) {
			if(!guardPart.equals("")){
				if(!guardPart.matches("\\s*L.+")&&stsHelper.getJavaScope(guardPart).equals(STSHelper.LOCAL)){
					localVariables += separator + guardPart;
					separator = ",";
				}
			}
		}
		return localVariables;
	}

	private String convertToJavaSyntax(String guard) {
		guard = guard.replaceAll("=", " == ");
		guard = guard.replaceAll("> == ", " >= ");
		guard = guard.replaceAll("< == ", " <= ");
		guard = guard.replaceAll("<>", " != ");
		guard = guard.replaceAll(" and ", " && ");
		guard = guard.replaceAll(" or ", " || " );
		guard = guard.replaceAll(" not ", " ! ");
		String regex = "[a-zA-Z_$][\\w_$]*"; 
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(guard);
        StringBuffer processedCode = new StringBuffer();
        while (matcher.find()) {
        	String find = matcher.group();
        	String replace = ""; 
        	if(find.equals("true")||find.equals("false")||find.matches("(\\d*.)?\\d+")){
				replace = find;
			}else{
				if(find.matches("\\s*L.+")){
					replace = "se.lnu.MonitorHelper.getSecurityLevel(\"" + find + "\")";
				}else if(stsHelper.getJavaScope(find).equals(STSHelper.LOCAL)){
					replace = "(" + stsHelper.getJavaType(find) + ") se.lnu.MonitorHelper.getLocalVariableValue(\"" + find + "\")";
				}else{
					replace = stsHelper.getJavaName(find);
				}
			}
			matcher.appendReplacement(processedCode, replace);
        }
        matcher.appendTail(processedCode);
		return processedCode.toString();
	}

	private void sort(String[] guardParts) {
		if(guardParts.length>1){
			boolean sorted = false;
			while (!sorted) {
				for (int i = 0; i < guardParts.length-1; i++) {
					sorted = true;
					if(guardParts[i].length()<guardParts[i+1].length()){
						String temp = guardParts[i];
						guardParts[i] = guardParts[i+1];
						guardParts[i+1] = temp;
						sorted = false;
					}
				}
			}
		}
	}

	private MethodDeclaration getMethodDeclaration(String qualifiedMethodName) {
		MethodDeclaration res = null;
		if(qualifiedMethodName.equals("se.lnu.DummyMethods.monitorablePoint")){
			JavaFileHelper javaFileHelper = new JavaFileHelper("", null, "", null);
			res = (MethodDeclaration) javaFileHelper.parseMethodDeclaration("static public void monitorablePoint(){}");
		}else{
			JavaFileHelper javaFileHelper = javaProjectHelper.getJavaFileHelper(qualifiedMethodName.substring(0, qualifiedMethodName.lastIndexOf(".")));
			String methodName = qualifiedMethodName.substring(qualifiedMethodName.lastIndexOf(".") + 1, qualifiedMethodName.length());
			for (MethodDeclaration methodDeclaration : ((TypeDeclaration)javaFileHelper.getCompilationUnit().types().get(0)).getMethods()) {
				if(methodDeclaration.getName().toString().equals(methodName)){
					res = methodDeclaration;
					break;
				}
			}
		}
		return res;
	}
}
