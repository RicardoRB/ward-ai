import util from 'util';
export const handler = async (event) => {
  return util.format('<p>%s</p>', 'ready');
};
