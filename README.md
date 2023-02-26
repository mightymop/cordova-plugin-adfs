# Android:

### 1. Add plugin
cordova plugin add https://github.com/mightymop/cordova-plugin-adfs.git
### 2. For Typescript add following code to main ts file: 
/// &lt;reference types="cordova-plugin-adfs" /&gt;<br/>

### 3. Before build:
add to config.xml > platform > android

```
	    <resource-file src="www/assets/config/development.json" target="app/src/main/res/raw/development.json" />
		<resource-file src="www/assets/config/production.json" target="app/src/main/res/raw/production.json" />
```

with json config params:

```
	   "adfs":{
			"baseurl": "https://dc2019.poldom.local/adfs",
			"key_userid": "upn",
			"client_id": "5ecf10b7-617a-4382-a67a-d35e30a66fae",
			"scope": "openid email profile offline_access allatclaims"
		}
```


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
