let limit = 1;
const result = limit = 2;

const reply = 1;
export const hooks = {
  follow(reply: {status: number}) {
    return reply.status === 302;
  },
};
export const outer = reply;

const response = {statusCode: 200};
export const followed = {
  followRedirect(response: {statusCode: number}) {
    if (response.statusCode === 302) {
      return true;
    }

    return false;
  },
};
export const status = response.statusCode;
