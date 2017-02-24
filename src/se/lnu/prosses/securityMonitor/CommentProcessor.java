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
 * 
 * \/*@CheckPoint*\/
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
				"/\\*"
					+ "[\\*\\s]*"
					+ "@((ObservationPoint)|(Init))"
					+ "[\\*\\s]*"
					+ "\\("
					+ "[\\*\\s]*"
						+ "\\{"
							+ "(" 
							+ "[\\*\\s]*"
								+ "@SecurityPolicy"	+ "[\\*\\s]*" + "\\([^\\)]*\\)"	+ "[\\*\\s]*"
								+ ",??"
							+ "[\\*\\s]*"
							+ ")*"
						+ "\\}"
					+ "[\\*\\s]"
					+ "*\\)"
					+ "[\\*\\s]*"
				+ "\\*/"
				+ "|"
				+ "/\\*"
				+ "[\\*\\s]*"
					+ "@CheckPoint"
				+ "[\\*\\s]*"	
				+ "\\*/"
				+ "[^\\{]*\\{";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(code);
        StringBuffer processedCode = new StringBuffer();
        while (matcher.find()) {
        	String annotation = matcher.group();
        	String processed = "";
        	if(annotation.contains("ObservationPoint")){
        		processed = processObservationPoint(annotation);
        	}else if(annotation.contains("Init")){
        		processed = processInit(annotation);
        	}else if(annotation.contains("CheckPoint")){
        		processed = processCheckPoint(annotation);
        	}
            matcher.appendReplacement(processedCode, processed);
        }
        matcher.appendTail(processedCode);
		Utils.writeTextFile(javaFilePath, processedCode.toString());
	}

	/**
	 * It extracts all the security policies declared in an initialization and then maps each of them to a proper statement 
	 * invoking the methods \"se.lnu.securityPolicy.init\".
	 * @param init an initialization point that is declared in a comment block
	 * @return one or more statements invoking the methods \"se.lnu.securityPolicy.observe\"
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	private static String processInit(String init) throws Exception {
		String securityPolicies = init.substring(init.indexOf("{"), init.lastIndexOf("}"));
		String regex = "@SecurityPolicy[\\*\\s]*\\([^\\)]*\\)";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(securityPolicies);
        String processed = "";
        while(matcher.find()){
        	processed += makeSecurityPolicyMethodInvocation(matcher.group(), "init") + "\n";
        }
		return processed;
	}

	/**
	 * It clears the annotation @CheckPoint before the method declaration and adds a statement invoking the method \"se.lnu.checkPoint\" 
	 * to the fist line of the method declaration. It also extracts the name of the declared method and passes it through \"se.lnu.checkPoint\".
	 * @param checkPoint a check point that is declared in a comment block
	 * @return the constant string \"se.lnu.checkPoint();\"
	 */
	private static String processCheckPoint(String checkPoint) {
		String processed = checkPoint.replace("/\\*[\\*\\s]*@CheckPoint[\\*\\s]*\\*/", "");
		String methodName = processed.replace("\\s*\\(", "(");
		methodName = methodName.replaceAll("\\s", " ").replaceAll("  ", "");
		methodName = methodName.substring(0, processed.indexOf("(")-1);
		String[] parts = processed.split(" ");
		methodName = parts[parts.length-1];
		processed += "se.lnu.DummyMethods.checkPoint(\"" + methodName + "\");";
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
		String securityPolicies = observationPoint.substring(observationPoint.indexOf("{"), observationPoint.lastIndexOf("}"));
		String regex = "@SecurityPolicy[\\*\\s]*\\([^\\)]*\\)";
		Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(securityPolicies);
        String processed = "";
        while(matcher.find()){
        	processed += makeSecurityPolicyMethodInvocation(matcher.group(), "observe") + "\n";
        }
		return processed;
	}

	/**
	 * It parses a declared security policy in order to sort declared parameters based on the parameters order of 
	 * the methods \"se.lnu.securityPolicy.observe\" or \"se.lnu.securityPolicy.init\" 
	 * and then returns a statement invoking a proper method with declared parameters in the security policy.
	 * @param annotation a security policy that is declared inside an observation point or initialization
	 * @param method determines which method must be invoked in the generated statement
	 * @return an invocation of the method \"se.lnu.SecurityPolicy\" with declared parameters in the security policy
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	static private String makeSecurityPolicyMethodInvocation(String annotation, String method) throws Exception {
		String[] wellOrderedParameters = getWellOrderedParameters(annotation);
		String SecurityPolicyMethodInvocation = "se.lnu.DummyMethods."
				+ method
				+ "("
				+ "\"" + wellOrderedParameters[0] + "\", "
				+ "\"" + wellOrderedParameters[1] + "\", "
				+ "\"" + wellOrderedParameters[2] + ");";
		return SecurityPolicyMethodInvocation;
	}

	/**
	 * It extracts and sort parameters declared in a security policy.
	 * @param annotation a security policy that is declared in a comment line
	 * @return sorted the security policy parameters based on the parameters order of the methods \"se.lnu.securityPolicy.init\" or \"se.lnu.securityPolicy.observe\".
	 * @throws Exception is thrown when some parameters of a security policy were wrongly defined
	 */
	static private String[] getWellOrderedParameters(String annotation) throws Exception {
		String annotationParameters = annotation.replaceFirst("//\\s*@SecurityPolicy\\s*\\(", "");
		annotationParameters = annotationParameters.substring(0, annotationParameters.length()-1);
		annotationParameters = annotationParameters.substring(annotationParameters.indexOf("\""), annotationParameters.lastIndexOf("\""));
		String[] wellOrderedParameters = new String[3];
		String[] parameters = annotationParameters.split("\"\\s*,\\s*\"");
		for (String parameter : parameters) {
			String name = parameter.substring(0, parameter.indexOf("="));
			String value = parameter.substring(parameter.indexOf("="), parameter.length());
			if(name.equals("expression") || name.equals("e")){
				wellOrderedParameters[0] = value; 
			}else if(name.equals("securityLevel") || name.equals("sl")){
				wellOrderedParameters[1] = value; 
			}else if(name.equals("policyType") || name.equals("pt")){
				wellOrderedParameters[2] = value;
			}else {
				throw new Exception(name + " was wrongly defined in the following security policy : " + annotation);
			}
		}
		return wellOrderedParameters;
	}
}
