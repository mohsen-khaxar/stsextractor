package se.lnu.prosses.securityMonitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class extracts and processes all the security annotations declared in comments blocks and 
 * maps them to proper method invocations that are processed when an STS is generated.
 * The security annotations have syntax as the same as java annotation but are declared in comment blocks.
 * The following security annotations are used for declaring observation points, initializations, and check points:
 *\/*@ObservationPoint(
 * *{
 * *@SecurityPolicy(expression="...", securityLevel="..", policyType="..."),
 * *.
 * *.
 * *.
 * *}
 * *)
 * *\/ 
 * 
 * \/*@Init(
 * *{
 * *@SecurityPolicy(expression="...", securityLevel="..", policyType="..."),
 * *.
 * *.
 * *.
 * *}
 * *)
 * *\/ 
 * \/*@ObservationPoint*\/
 * \/*@EntryPoint*\/
 * \/*@CheckPoint*\/
 * \/*@SecurityPolicy(securityLevel="..", policyType="...")*\/
 * \/*@SecurityInit(securityLevel="..", policyType="...")*\/
 * 
 * Instead of the keywords expression, securityLevel, and policyType, it can be to use e, sl, and pt respectively.
 * Note that the security annotations must be just declared in comment blocks not comment line, otherwise they are ignored. 
 * The annotations @ObservationPoint and @Init must be declared just before of invocation of third-party methods while, 
 * the annotation @CheckPoint must be declared before method declarations.
 * @author mohsen
 *
 */
public class CommentProcessor {
	/**
	 * It processes all the comment blocks of the given source code to extract the comments declaring observation points, check points, initializations, and security policies.
	 * Then, it replaces those comments with corresponding invocations of the methods \"se.lnu.checkPoint\", \"se.lnu.securityPolicy.observe\", and \"se.lnu.securityPolicy.init\" 
	 * and finally it overwrites the old java file with the processed source code. 
	 * The injected method invocations are processed when an STS is generated instead of comment blocks.
	 * Each security policy must be declared in one comment line in the following form : @SecurityPolicy(expression="...", securityLevel="...", policyType="...").
	 * Since the parameters are defined as key-value list, it is allowed to change their order.
	 * @param javaFilePath indicates to path of a java file
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	static public void process(String javaFilePath) throws Exception {
		String code = String.valueOf(Utils.readTextFile(javaFilePath));
		String regex = 
				"\\s*/\\*\\s*@\\s*(ObservationPoint)\\s*\\*/" + "[^;]+;" 
				+ "|"
				+ "/\\*\\s*@\\s*((CheckPoint)|(EntryPoint))\\s*\\*/" + "[^\\{]*\\{"
				+ "|"
				+ "/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/[^\\;]+[\\;]";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);
        StringBuffer processedCode = new StringBuffer();
        String securityInits = "";
    	String processed = "";
        while (matcher.find()) {
        	String annotation = matcher.group();
        	if(annotation.contains("ObservationPoint")){
        		processed = processObservationPoint(annotation);
        	}else if(annotation.contains("EntryPoint")){
        		processed = processEntryPoint(annotation);
        	}else if(annotation.contains("CheckPoint")){
        		processed = processCheckPoint(annotation);
        	}else if(annotation.contains("SecurityInit")){
        		securityInits += processSecurityInit(annotation);
        		processed = annotation.replaceAll("/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/", "");
        	}else{
        		throw new Exception(annotation + " is not a valid annotation.");
        	}
            matcher.appendReplacement(processedCode, processed);
        }
        matcher.appendTail(processedCode);
        processed = injectSecurityInits(processedCode.toString(), securityInits);
		Utils.writeTextFile(javaFilePath, processed);
	}

	private static String injectSecurityInits(String code, String securityInits) {
		code = code.replaceAll("//[^\n]*[\n]|/\\*.*\\*/", "");
		String regex = "\\s*public[^;\\(]+[\\(][^;\\{]+\\{(.*entryPoint\\s*\\([^;]+;)?"; 
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);
        StringBuffer processedCode = new StringBuffer();
        while (matcher.find()) {
        	String processed = matcher.group();
        	processed += securityInits;
            matcher.appendReplacement(processedCode, processed);
        }
        matcher.appendTail(processedCode);
		return processedCode.toString();
	}

	private static String processSecurityInit(String annotation) throws Exception {
		String wellOrderedParameters = getWellOrderedParameters(annotation);
		String processed = annotation.replaceAll("/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/\\s*", "");
		processed = "se.lnu.DummyMethods.init((Object)" + processed.substring(0, processed.length()-1).split("\\s+")[1]
				+ ", " + wellOrderedParameters	+ ");";
		return processed;
	}

	private static String processSecurityPolicy(String annotation) throws Exception {
		String wellOrderedParameters = getWellOrderedParameters(annotation);
		String processed = annotation.replaceAll("/\\*\\s*@\\s*(SecurityPolicy)\\s*[^\\)]+\\)\\s*\\*/", "");
		processed = "se.lnu.DummyMethods.observe((Object)" + processed.substring(0, processed.length()-1)
				+ ", " + wellOrderedParameters	+ ");";
		return processed;
	}

	/**
	 * It extracts all the security policies declared in an initialization and then maps each of them to a proper statement 
	 * invoking the methods \"se.lnu.securityPolicy.init\".
	 * @param init an initialization point that is declared in a comment block
	 * @return one or more statements invoking the methods \"se.lnu.securityPolicy.observe\"
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	private static String processEntryPoint(String entryPoint) throws Exception {
		String processed = entryPoint.replaceAll("\\s*/\\*\\s*@\\s*EntryPoint\\s*\\*/\\s*", "");
		boolean isCheckPoint = false;
		if(processed.matches("/\\*\\s*@\\s*CheckPoint\\s*\\*/[\\s\\S]*")){
			processed = processed.replaceAll("\\s*/\\*\\s*@\\s*CheckPoint\\s*\\*/\\s*", "");
			isCheckPoint = true;
		}
		String methodName = processed.replaceAll("\\s*\\(", "(");
		methodName = methodName.replaceAll("\\s+", " ");
		methodName = methodName.substring(0, methodName.indexOf("("));
		String[] parts = methodName.split(" ");
		methodName = parts[parts.length-1];
		processed += "se.lnu.DummyMethods.entryPoint(\"" + methodName + "\");";
		if(isCheckPoint){
			processed += "se.lnu.DummyMethods.checkPoint(\"" + methodName + "\");";
		}
		
		String regex = "/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/[^\\,\\)]+[\\,\\)]";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(processed);
        StringBuffer stringBuffer = new StringBuffer();
        String inits = "";
        while (matcher.find()) {
        	String annotation = matcher.group();
        	inits += processSecurityInit(annotation);
        	annotation = annotation.replaceAll("/\\*\\s*@\\s*SecurityInit\\s*[^\\)]+\\)\\s*\\*/", "");
        	matcher.appendReplacement(stringBuffer, annotation);
        }
        matcher.appendTail(stringBuffer);
        processed = stringBuffer.toString() + inits;
		return processed;
	}

	/**
	 * It clears the annotation @CheckPoint before the method declaration and adds a statement invoking the method \"se.lnu.checkPoint\" 
	 * to the fist line of the method declaration. It also extracts the name of the declared method and passes it through \"se.lnu.checkPoint\".
	 * @param checkPoint a check point that is declared in a comment block
	 * @return the constant string \"se.lnu.checkPoint();\"
	 */
	private static String processCheckPoint(String checkPoint) {
		String processed = checkPoint.replaceAll("/\\*\\s*@\\s*CheckPoint\\s*\\*/", "");
		boolean isEntryPoint = false;
		if(checkPoint.matches("/\\*\\s*@\\s*EntryPoint\\s*\\*/[\\s\\S]+")){
			processed = checkPoint.replace("/\\*\\s*@\\s*EntryPoint\\s*\\*/", "");
			isEntryPoint = true;
		}
		String methodName = processed.replaceAll("\\s*\\(", "(");
		methodName = methodName.replaceAll("\\s+", " ");
		methodName = methodName.substring(0, methodName.indexOf("("));
		String[] parts = methodName.split(" ");
		methodName = parts[parts.length-1];
		processed += "se.lnu.DummyMethods.checkPoint(\"" + methodName + "\");";
		if(isEntryPoint){
			processed += "se.lnu.DummyMethods.entryPoint(\"" + methodName + "\");";
		}
		return processed;
	}

	/**
	 * It extracts all the security policies declared in an observation point and then maps each of them to a proper statement 
	 * invoking the methods \"se.lnu.securityPolicy.observe\".
	 * @param observationPoint an observation point that is declared in a comment block
	 * @return one or more statements invoking the methods \"se.lnu.securityPolicy.observe\"
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	private static String processObservationPoint(String observationPoint) throws Exception {
		String processed = observationPoint.replaceAll("\\s*/\\*\\s*@\\s*ObservationPoint\\s*\\*/", "");
		String regex = "/\\*\\s*@\\s*SecurityPolicy\\s*[^\\)]+\\)\\s*\\*/[^\\,\\)]+[\\,\\)]";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(processed);
        StringBuffer stringBuffer = new StringBuffer();
        String observes = "";
        while (matcher.find()) {
        	String annotation = matcher.group();
        	observes += processSecurityPolicy(annotation);
        	annotation = annotation.replaceAll("/\\*\\s*@\\s*SecurityPolicy\\s*[^\\)]+\\)\\s*\\*/", "");
        	matcher.appendReplacement(stringBuffer, annotation);
        }
        matcher.appendTail(stringBuffer);
        processed = stringBuffer.toString();
        processed = observes + processed;
		return processed;
	}

	/**
	 * It extracts and sort parameters declared in a security policy.
	 * @param annotation a security policy that is declared in a comment line
	 * @return sorted the security policy parameters based on the parameters order of the methods \"se.lnu.securityPolicy.init\" or \"se.lnu.securityPolicy.observe\".
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	static private String getWellOrderedParameters(String annotation) throws Exception {
		String annotationParameters = annotation.replaceFirst("/\\*\\s*@(SecurityInit|SecurityPolicy)\\s*\\(", "");
		annotationParameters = annotationParameters.substring(0, annotationParameters.indexOf(")"));
		annotationParameters = annotationParameters.substring(0, annotationParameters.lastIndexOf("\"")).replaceAll("\"", "").replaceAll("\\s", "");
		String[] wellOrderedParameters = new String[2];
		String[] parameters = annotationParameters.split(",");
		for (String parameter : parameters) {
			String name = parameter.substring(0, parameter.indexOf("="));
			String value = parameter.substring(parameter.indexOf("=")+1, parameter.length());
			if(name.equals("securityLevel") || name.equals("sl")){
				wellOrderedParameters[0] = value; 
			}else if(name.equals("policyType") || name.equals("pt")){
				wellOrderedParameters[1] = value;
			}else {
				throw new Exception(name + " was wrongly defined in the following security policy : " + annotation);
			}
		}
		return "\"" + wellOrderedParameters[0] + "\", \"" + wellOrderedParameters[1] + "\"";
	}
}
