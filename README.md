INSTALL :
1. Install REAX following the instructions in http://nberth.space/opam
2. Build the project using maven.

 INSTRUCTION :

1. Annotate your java application using security annotations.
2. Set the variables "sourcePath", "targetPath", "classPath", and "includingFilter" with proper values in the method "se.lnu.prosses.securityMonitor.Main.main"
	* sourcePath is the directory of the application source code
	* targetPath is the directory to save the outputs
	* classPath points to all dependencies of your application including the directory of your application source code
	* includingFilter is a java regular expression that shows the classes that should be processed. Other classes are mentioned as third-party classes.
3. Build and run this application
4. Put this application in your application class path and build and run yours

SECURITY ANNOTATIONS :

/*@ObservationPoint*/ must be declared just before invocation of third-party methods and represents an observation point.

/*@SecurityPolicy(securityLevel="..", policyType="..")*/ must be declared just before arguments of third-party method invocations and specifies security level and policy type of the method arguments. Explicit and implicit policy types are represented by the characters "X" and "L” respectively. The characters "L" and "H" are used to specify the security levels low and high respectively. 

/*@EntryPoint*/ must be declared before method declarations and represents the points that your application starts from.

/*@CheckPoint*/ must be declared before method declarations and represents a check point.

/*@SecurityInit(securityLevel="..", policyType="..")*/ must be used just before methods parameter or class field declarations and specifies the initial security level for the mentioned method parameter or class field.

Annotations Example  : 


```java
package se.lnu;
public class CaseStudy {
	/*@SecurityInit(securityLevel="LL", policyType="XI")*/int location=2;
	/*@SecurityInit(securityLevel="LL", policyType="XI")*/int MaxDistance=10;
	/*@SecurityInit(securityLevel="LL", policyType="XI")*/int friendsNum=2;
	/*@SecurityInit(securityLevel="LL", policyType="XI")*/int friendLocation=3;

	/* @CheckPoint */
	/* @EntryPoint */
	public void run(/*@SecurityInit(securityLevel="HH", policyType="XI")*/int sl) {
		int estimate = 0;
		estimate = estimatLocation(sl);
		/* @ObservationPoint (default="System.out.println(-10)")*/
		System.out.println(/* @SecurityPolicy(securityLevel="LL", policyType="XI") */estimate);
	}
	
	/*@CheckPoint*/
	int estimatLocation(int strangerLoc) {
		int x = strangerLoc * location;
		if (x > 0) {
			int d = getDistance(location, strangerLoc);
			boolean exist = true;
			if (d < MaxDistance) {
				x = location;
				exist = true;
			} else {
				boolean b = false;
				int i = 0;
				while (!b && i < friendsNum) {
					int friendLoc = getFriendLocAt(i);
					d = getDistance(friendLoc, strangerLoc);
					if (d < MaxDistance) {
						exist = true;
						x = friendLoc;
						b = true;
					} else {
						exist = false;
					}
					i = i +1;
				}
			}
			if (exist) {
				return x;
			} else {
				return -1;
			}
		} else {
			return -1;
		}
	}
	private int getFriendLocAt(int i) {
		return friendLocation;
	}
	private int getDistance(int location1, int location2) {
		return (location2 - location1) * (location2 - location1);
	}
}
```


