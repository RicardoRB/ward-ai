function f() {
  try {
    return 1;
  } finally {
    cleanup();
  }
}
function cleanup() {}

export async function stop(server: {close(callback: (error?: Error) => void): void}) {
  try {
    await Promise.resolve();
  } finally {
    await new Promise<void>((resolve, reject) => {
      server.close(error => {
        if (error) {
          reject(error);
          return;
        }

        resolve();
      });
    });
  }
}
