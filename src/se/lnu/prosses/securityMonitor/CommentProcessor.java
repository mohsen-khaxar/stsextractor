package se.lnu.prosses.securityMonitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class extracts and processes all the security annotations declared in comments blocks and 
 * maps them to proper method invocations that are processed when an STS is generated.
 * The security annotations have syntax as well as java annotations but are declared in comment blocks.
 * The following security annotations are employed for declaring observation points, entry points, check points, security policies, and initializations:
 * \/*@ObservationPoint*\/
 * \/*@EntryPoint*\/
 * \/*@CheckPoint*\/
 * \/*@SecurityPolicy(securityLevel="..", policyType="...")*\/
 * \/*@SecurityInit(securityLevel="..", policyType="...")*\/
 * 
 * Instead of the keywords securityLevel and policyType, sl and pt may be used respectively.
 * Note that the security annotations must be just declared in comment blocks not comment line, otherwise they are ignored. 
 * Each comment block just contains one annotation.
 * The annotations @ObservationPoint must be declared just before invocation of third-party methods while, 
 * the annotation @CheckPoint and @EntryPoint must be declared before method declarations. 
 * The annotation @SecurityPolicy must be declared just before arguments of third-party method invocations and @SecurityInit must be used just before
 * methods parameter or class field declarations. 
 * @author mohsen
 *
 */
public class CommentProcessor {
	/**
	 * It processes all the comment blocks of the given source code to extract the comments declaring observation points, check points, entry points, initializations, and security policies.
	 * Then, it replaces those comments with corresponding invocations of the methods \"se.lnu.DummyMethods.checkPoint\", \"se.lnu.DummyMethods.entryPoint\", \"se.lnu.DummyMethods.init\", \"se.lnu.DummyMethods.observe\"  
	 * and finally it overwrites the old java file with the processed source code. 
	 * The injected method invocations are processed when an STS is generated instead of comment blocks.
	 * Each security security or initialization must be declared in one comment block in the following form : @[SecurityPolicy|SecurityInit](securityLevel="...", policyType="...")
	 * where securityLevel and policyType may be one or two characters. The first character in securityLevel shows the security level of the expression of interest with the policy type specified by the first character of policyType.
	 * The second character works as well as the first one. The characters "H" and "L" are used to indicate high and low security levels while "I" and "X" are shown the policy types implicit and explicit respectively. 
	 * Since the parameters are defined as key-value list, it is allowed to change their order.
	 * The following is a code snippet containing all the security annotations :
	 * 
	 * public class CaseStudy {
	 * .
	 * .
	 * .
	 * \/*@SecurityInit(securityLevel="LL", policyType="XI")*\/int friendLocation;
	 * 
	 * \/* @CheckPoint *\/
	 * \/* @EntryPoint *\/
	 * public void run(\/*@SecurityInit(securityLevel="HL", policyType="XI")*\/int sl) {
	 * 	int estimate = 0;
	 *	estimate = estimatLocation(sl);
	 *	\/* @ObservationPoint *\/
	 *	System.out.println(\/* @SecurityPolicy(securityLevel="LL", policyType="XI") *\/estimate);
	 * }
	 * .
	 * .
	 * .
	 * }
	 * @param javaFilePath indicates to path of a java file
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	static public void process(String javaFilePath) throws Exception {
		String code = String.valueOf(Utils.readTextFile(javaFilePath));
		String regex = 
				"\\s*/\\*\\s*@\\s*(ObservationPoint)[^\\*]*\\*/" + "[^;]+;" 
				+ "|"
				+ "/\\*\\s*@\\s*((CheckPoint)|(EntryPoint))\\s*\\*/" + "[^\\{]*\\{"
				+ "|"
				+ "/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/[^\\;=]+[\\;=]";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);
        StringBuffer processedCode = new StringBuffer();
        String securityInits = "";
    	String processed = "";
//    	extracts all the comment blocks containing security annotation
        while (matcher.find()) {
        	String annotation = matcher.group();
        	if(annotation.contains("ObservationPoint")){
        		processed = processObservationPoint(annotation, matcher.start());
        	}else if(annotation.contains("EntryPoint")){
        		processed = processEntryPoint(annotation);
        	}else if(annotation.contains("CheckPoint")){
        		processed = processCheckPoint(annotation);
        	}else if(annotation.contains("SecurityInit")){
//        		in this case, gathers all security initialization assignments to inject them in the beginning of every method that is annotated as an entry point
        		securityInits += processSecurityInit(annotation);
        		processed = annotation.replaceAll("/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/", "");
        	}else{
        		throw new Exception(annotation + " is not a valid annotation.");
        	}
//        	replaces generated method invocations with the comment block.
            matcher.appendReplacement(processedCode, processed);
        }
        matcher.appendTail(processedCode);
//      injects all the security initialization assignments in the beginning of every method that is annotated as an entry point 
//      note that \"se.lnu.DummyMethods.entryPoint\" must be injected before the security initialization assignments
        processed = injectSecurityInits(processedCode.toString(), securityInits);
		Utils.writeTextFile(javaFilePath, processed);
	}

	
	/**
	 * It injects the content of securityInits that shows security initialization assignments in the beginning of every method annotated as an entry point.
	 * @param code is a source code containing java class declaration that is target of injection
	 * @param securityInits are security initialization assignments that must be injected in the beginning of every entry point
	 * @return is code in which securityInits is injected
	 */
	public static String injectSecurityInits(String code, String securityInits) {
		code = code.replaceAll("//[^\n]*[\n]|/\\*.*\\*/", "");
		String regex = "\\s*public[^;\\(]+[\\(][^;\\{]+\\{(.*entryPoint\\s*\\([^;]+;)?"; 
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);
        StringBuffer processedCode = new StringBuffer();
//    	extracts signature of all the public method annotated as entry point
        while (matcher.find()) {
        	String processed = matcher.group();
        	processed += securityInits;
//        	append securityInits after the character '{' that is the beginning of the method
            matcher.appendReplacement(processedCode, processed);
        }
        matcher.appendTail(processedCode);
		return processedCode.toString();
	}

	/**
	 * It generates the method invocation \"se.lnu.DummyMethods.init\" based on annotation declaring a security initialization.
	 * It extracts the parameter that comes immediately after @SecurityInit declaration 
	 * and passes it along with security level and policy type to \"se.lnu.DummyMethods.init\".
	 * @param annotation contains a comment block declaring a @SecurityInit along with a parameter declaration that is target of the annotation
	 * @return is the method invocation that defines a security initialization based on annotation
	 * @throws Exception rethrows the exception raised by the method getWellOrderedParameters
	 */
	private static String processSecurityInit(String annotation) throws Exception {
		String wellOrderedParameters = getWellOrderedParameters(annotation);
		String processed = annotation.replaceAll("/\\*\\s*@\\s*(SecurityInit)\\s*[^\\)]+\\)\\s*\\*/\\s*", "");
		processed = "se.lnu.DummyMethods.init((Object)" + processed.substring(0, processed.length()-1).split("\\s+")[1]
				+ ", " + wellOrderedParameters	+ ");";
		return processed;
	}

	/**
	 * It generates the method invocation \"se.lnu.DummyMethods.observe\" based on annotation declaring a security policy.
	 * It extracts the argument or the class field that comes immediately after @SecurityPolicy declaration 
	 * and passes it along with security level and policy type to \"se.lnu.DummyMethods.observe\".
	 * @param annotation contains a comment block declaring a @SecurityPolicy along with a argument or a class field declaration that is target of the annotation
	 * @return is the method invocation that defines a security policy based on annotation
	 * @throws Exception rethrows the exception raised by the method getWellOrderedParameters
	 */
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
	 * @param i 
	 * @return one or more statements invoking the methods \"se.lnu.securityPolicy.observe\"
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	private static String processObservationPoint(String observationPoint, int startPosition) throws Exception {
		String regex = "\\s*/\\*\\s*@\\s*ObservationPoint[^\\*]*\\*/\\s*";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(observationPoint);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
        	String annotation = matcher.group();
        	if(!annotation.contains("default")){
        		annotation = "";
        	}else{
        		annotation = annotation.replaceAll("\\s*/\\*\\s*@\\s*ObservationPoint[^\\=]+\\=", 
        				"se.lnu.DummyMethods.countermeasure(" + (startPosition+annotation.length()) + ", ")
        				.replaceAll("\\*/", "")+ ";";
        	}
        	matcher.appendReplacement(stringBuffer, annotation);
        }
        matcher.appendTail(stringBuffer);
        String processed = stringBuffer.toString();
		regex = "/\\*\\s*@\\s*SecurityPolicy\\s*[^\\)]+\\)\\s*\\*/[^\\,\\)]+[\\,\\)]";
		pattern = Pattern.compile(regex);
        matcher = pattern.matcher(processed);
        stringBuffer = new StringBuffer();
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
