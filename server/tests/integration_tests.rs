use std::{path::PathBuf, time::Duration};
use std::thread;
use std::sync::mpsc;
use rdev::{listen, EventType, Button};
use std::process::Command;

use mobile_tilt_mouse::connection_handler;

//
// Do not use mouse or keyboard during these tests, as real mouse/keyboard events are evaluated.
//
//
// Test command for running the following integration tests (must run sequentially, hence --test-threads=1):
// cargo test --test integration_tests -- --ignored --test-threads=1

#[cfg(target_os = "macos")]
const IOS_SIMULATOR_DEVICE: &str = "iPhone 17";

const ANDROID_EMULATOR_DEVICE: &str = "Pixel_9_API_36.0";


fn receive_button_clicks() {
    // move mouse cursor to top left corner of display to minimize risk of causing harm by mouse clicks
    rdev::simulate(&EventType::MouseMove { x: 0.0, y: 0.0 }).unwrap();

    // start mouse server
    thread::spawn(move || {
        connection_handler(true).unwrap();
    });

    let (tx, rx) = mpsc::channel();

    // listen to mouse/keyboard events
    thread::spawn(move || {
        listen(move |event| {
            tx.send(event.event_type).unwrap();
        }).unwrap();
      
    });

    assert_eq!(rx.recv_timeout(Duration::from_millis(180000)), Ok(EventType::ButtonPress(Button::Left)));
    assert_eq!(rx.recv_timeout(Duration::from_millis(200)),    Ok(EventType::ButtonRelease(Button::Left)));
    assert_eq!(rx.recv_timeout(Duration::from_millis(1000)),   Ok(EventType::ButtonPress(Button::Right)));
    assert_eq!(rx.recv_timeout(Duration::from_millis(200)),    Ok(EventType::ButtonRelease(Button::Right)));
}


#[test]
#[ignore]
#[cfg(target_os = "macos")]
// Start iOS simulator and run test case there which clicks buttons. Server should receive the button click commands.
// Test command:
// cargo test --test integration_tests -- test_ios_receive_button_clicks --ignored
fn test_ios_receive_button_clicks() {
    // iOS test part is only run if the following environment variable is set: TEST_RUNNER_Integration_Testing="YES"
    let mut simulator_process = Command::new("xcodebuild")
    .env("TEST_RUNNER_Integration_Testing", "YES")
    .args(["test", "-scheme", "MobileTiltMouse", "-destination", format!("platform=iOS Simulator,name={}", IOS_SIMULATOR_DEVICE).as_str(), "-only-testing", "MobileTiltMouseUITests/IntegrationTests/testSendButtonClicks"])
    .current_dir("../AppiOS")
    .spawn()
    .expect("Failed to execute integration test counterpart in Xcode.");

    receive_button_clicks();

    simulator_process.wait().expect("Failed to wait for simulator process.");
}

#[test]
#[ignore]
// Start Android emulator and run test case there which clicks buttons. Server should receive the button click commands.
// Test command:
// cargo test --test integration_tests -- test_android_receive_button_clicks --ignored
fn test_android_receive_button_clicks() {
    let emulator_path =
    if cfg!(target_os = "macos") {
        // get home directory of user and add remaining emulator path
        let homedir = std::env::home_dir().expect("Failed to get home directory.");
        homedir.join("Library/Android/sdk/emulator/emulator")
    } else if cfg!(target_os = "linux") {
        let homedir = std::env::home_dir().expect("Failed to get home directory.");
        homedir.join("Android/sdk/emulator/emulator")
    } else if cfg!(target_os = "windows") {
        let appdir = std::env::var("LOCALAPPDATA").expect("Failed to get environment variable LOCALAPPDATA.");
        PathBuf::from(appdir).join(r"Android\Sdk\emulator\emulator.exe")
    } else {
        panic!("OS not supported")
    };

    // start the Android emulator, its process will be killed at end of test
    #[allow(clippy::zombie_processes)]
    let mut emulator_process = Command::new(emulator_path)
    .args(["-avd", ANDROID_EMULATOR_DEVICE])
    .spawn()
    .expect("Failed to execute Android emulator.");

    // wait for the emulator to start
    thread::sleep(Duration::from_secs(20));

    // run the Android counterpart of the integration test
    let mut gradlew_process = Command::new("../AppAndroid/gradlew")
    .args([
        "connectedAndroidTest", 
        "-Pandroid.testInstrumentationRunnerArguments.class=com.example.mobiletiltmouse.IntegrationTest", 
        "-Pandroid.testInstrumentationRunnerArguments.remoteArg=integrationTest"
    ])
    .current_dir("../AppAndroid")
    .spawn()
    .expect("Failed to execute integration test counterpart in Android.");

    // run test
    receive_button_clicks();

    // kill emulator after gradlew process has finished
    gradlew_process.wait().expect("Failed to wait for gradlew process.");
    emulator_process.kill().expect("Failed to kill Android emulator.");
}
