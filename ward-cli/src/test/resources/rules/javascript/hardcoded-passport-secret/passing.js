export const strategy = new GoogleStrategy({ clientID: id, clientSecret: process.env.SECRET }, verify);
