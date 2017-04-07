Install :
1. Install REAX following the instructions in http://nberth.space/opam
2. Build the project using maven.

Usage :
1. Annotate your java application using security annotations.
2. set the variables "sourcePath", "targetPath", "classPath", and "includingFilter" with proper values 
in the method "se.lnu.prosses.securityMonitor.Main.main"
    -sourcePath indicates to the directory of your application source code
    -targetPath indicates to the directory you want to save the outputs
    -classPath indicates to all dependencies of your application including the directory of your application source code
    -includingFilter indicates to a java regular expression determining the classes that should be processed. 
     Other classes are mentioned as third-party classes.
3. Build and run our application
4. Put our application in your application class path and build and run yours

Security Annotations :

/\*@ObservationPoint\*/ must be declared just before invocation of third-party methods and determined an observation point.

/\*@SecurityPolicy(securityLevel="..", policyType="..")\*/ must be declared just before arguments of third-party method invocations
and specifies the mentioned arguments has which security level in addition to policy type

/\*@EntryPoint\*/ must be declared before method declarations and determines the application starts from which point.

/\*@CheckPoint\*/ must be declared before method declarations and determines check points.

/\*@SecurityInit(securityLevel="..", policyType="..")\*/ must be used just before methods parameter or class field declarations
and specifies the initial security level for the mentioned method parameter or class field.

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


