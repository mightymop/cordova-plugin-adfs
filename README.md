# Android:

### 1. Add plugin
cordova plugin add https://github.com/mightymop/cordova-plugin-adfs.git#minimal
### 2. For Typescript add following code to main ts file: 
/// &lt;reference types="cordova-plugin-adfs" /&gt;<br/>

### 3. Build And Install Authenticator for Android and configure
https://github.com/mightymop/cordova-plugin-adfs/tree/authenticator

### 4. Usage:

methods:

```
	login: function (success, error )
	logout: function (success, error ) 
	checklogin: function (success, error) 	
	getIDToken: function (success, error)
	getAccessToken: function (success, error) 
	getRefreshToken: function (success, error) 	
	
```

### 5. Reason why authenticator was extracted:

If you have multiple apps which uses the same cordova-plugin-adfs plugin, 
then the authenticator of the first installed app was used. 
Example: App A und B were installed. If you login on App B the Authenticator of App A was used.
Furthermore you are now able to configure the ADFS preferences one time for all single sign on apps.