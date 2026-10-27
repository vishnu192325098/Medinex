/**
 * WebdriverIO configuration for Medinex Android Native Java Application
 * Package: com.example.medinex
 * APK: app/build/outputs/apk/debug/app-debug.apk
 */

const path = require('path');

exports.config = {
    runner: 'local',
    port: 4723,
    specs: [
        './specs/**/*.spec.js'
    ],
    maxInstances: 1,
    capabilities: [{
        platformName: 'Android',
        'appium:automationName': 'UiAutomator2',
        'appium:deviceName': process.env.ANDROID_DEVICE_NAME || 'Android Emulator',
        'appium:app': path.resolve(__dirname, '../../app/build/outputs/apk/debug/app-debug.apk'),
        'appium:appPackage': 'com.example.medinex',
        'appium:appActivity': 'com.example.medinex.SplashActivity',
        'appium:noReset': false,
        'appium:fullReset': false,
        'appium:newCommandTimeout': 240,
        'appium:autoGrantPermissions': true
    }],
    logLevel: 'info',
    bail: 0,
    baseUrl: 'http://localhost',
    waitforTimeout: 10000,
    connectionRetryTimeout: 120000,
    connectionRetryCount: 3,
    services: ['appium'],
    framework: 'mocha',
    reporters: ['spec'],
    mochaOpts: {
        ui: 'bdd',
        timeout: 60000
    }
};
