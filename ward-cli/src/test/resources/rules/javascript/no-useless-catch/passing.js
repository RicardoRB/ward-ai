function f() {
  try {
    run();
  } catch (error) {
    log(error);
    throw error;
  }
}
function run() {}
function log() {}
