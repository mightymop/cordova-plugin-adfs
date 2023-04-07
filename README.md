# Android:

### 1. Add plugin
cordova plugin add https://github.com/mightymop/cordova-plugin-adfs.git#minimal
### 2. For Typescript add following code to main ts file: 
/// &lt;reference types="cordova-plugin-adfs" /&gt;<br/>

### 3. Build And Install Authenticator for Android and configure
https://github.com/mightymop/cordova-plugin-adfs.git#authenticator

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
