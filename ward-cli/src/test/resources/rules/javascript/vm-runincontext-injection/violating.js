const vm = require('vm');
export const handler = async (event) => {
  return vm.runInNewContext(event.body, {});
};
