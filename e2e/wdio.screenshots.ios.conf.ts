import { execSync } from 'child_process';
import { config as sharedConfig } from './wdio.shared.conf.js';

function findIosAppPath(): string {
  if (process.env.E2E_APP_PATH) return process.env.E2E_APP_PATH;
  const deviceName = process.env.SCREENSHOT_DEVICE || 'iPhone 16 Pro Max';
  const buildDir = execSync(
    `xcodebuild -scheme iosApp -project ../iosApp/iosApp.xcodeproj ` +
    `-destination 'platform=iOS Simulator,name=${deviceName}' ` +
    `-configuration Debug -showBuildSettings 2>/dev/null | ` +
    `grep -m1 "BUILT_PRODUCTS_DIR" | awk '{print $3}'`
  ).toString().trim();

  if (!buildDir) {
    throw new Error(
      'Could not determine iOS app path. Build the app first or set E2E_APP_PATH.'
    );
  }
  return `${buildDir}/iosApp.app`;
}

const lang = process.env.SCREENSHOT_LANG || 'en';
const locale = process.env.SCREENSHOT_LOCALE || 'en_US';
const bgPath = process.env.SCREENSHOT_BACKGROUND || '';

export const config: WebdriverIO.Config = {
  ...sharedConfig,

  specs: ['./specs/screenshots.spec.ts'],

  capabilities: [{
    platformName: 'iOS',
    'appium:automationName': 'XCUITest',
    'appium:app': findIosAppPath(),
    'appium:bundleId': 'com.flowmaticc.qrflow',
    'appium:deviceName': process.env.SCREENSHOT_DEVICE || 'iPhone 16 Pro Max',
    'appium:platformVersion': process.env.E2E_PLATFORM_VERSION || '26.2',
    ...(process.env.SCREENSHOT_UDID ? { 'appium:udid': process.env.SCREENSHOT_UDID } : {}),
    'appium:autoAcceptAlerts': true,
    'appium:newCommandTimeout': 240,
    'appium:noReset': false,
    'appium:language': lang,
    'appium:locale': locale,
  }],

  specFileRetries: 0,

  reporters: ['spec'],

  onPrepare() {
    if (typeof sharedConfig.onPrepare === 'function') sharedConfig.onPrepare({} as any, [] as any);
  },

  async before(_capabilities: any, _specs: string[]) {
    if (typeof sharedConfig.before === 'function') {
      await (sharedConfig.before as Function)(_capabilities, _specs);
    }

    // Write demo camera flag file so the app skips onboarding + camera permission.
    // Then persist AppleLanguages in the app's user defaults so the locale survives
    // the terminate/reactivate cycle (Appium's -AppleLanguages launch arg is lost
    // on reactivation, which breaks Compose Multiplatform's stringResource locale).
    try {
      execSync('xcrun simctl privacy booted grant photos com.flowmaticc.qrflow');
      const dataContainer = execSync(
        'xcrun simctl get_app_container booted com.flowmaticc.qrflow data'
      ).toString().trim();
      execSync(`touch '${dataContainer}/Documents/.demo_camera'`);
      if (bgPath) {
        execSync(`echo '${bgPath}' > '${dataContainer}/Documents/.demo_camera_bg'`);
      }
      // Persist locale in app user defaults so Compose picks it up after restart
      execSync(
        `xcrun simctl spawn booted defaults write com.flowmaticc.qrflow AppleLanguages -array '${lang}'`
      );
      execSync(
        `xcrun simctl spawn booted defaults write com.flowmaticc.qrflow AppleLocale '${locale}'`
      );
      await driver.terminateApp('com.flowmaticc.qrflow');
      await driver.activateApp('com.flowmaticc.qrflow');
    } catch (e) {
      console.warn('[ios] Failed to set up demo mode:', e);
    }

    // Clean status bar
    try {
      execSync(
        'xcrun simctl status_bar booted override ' +
        '--time "9:41" --batteryState charged --batteryLevel 100 ' +
        '--cellularMode active --cellularBars 4 --wifiBars 3'
      );
    } catch (e) {
      console.warn('[ios] Failed to override status bar:', e);
    }
  },
};
