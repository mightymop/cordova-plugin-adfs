var exec = require('cordova/exec');
var PLUGIN_NAME = 'adfs';

var adfs = {
	
	login: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'login', []);
	},
	checklogin: function (success, error ) {
		exec(success, error, PLUGIN_NAME, 'checklogin', []);
	},
	logout: function (success, error ) {
		exec(success, error, PLUGIN_NAME, 'logout', []);
	},
	getIDToken: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getIDToken', []);
	},
	getAccessToken: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getAccessToken', []);
	},
	getRefreshToken: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getRefreshToken', []);
	},
	getRefreshTokenExpTime: function (success, error) {
		exec(success, error, PLUGIN_NAME, 'getRefreshTokenExpTime', []);
	}

};

module.exports = adfs;
