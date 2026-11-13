import argon2 from 'argon2';

export async function strong(password) {
  return argon2.hash(password, { memoryCost: 65536, timeCost: 3, parallelism: 4 });
}

export async function powerOfTwo(password) {
  return argon2.hash(password, { memoryCost: 2 ** 16 });
}

export async function defaults(password) {
  return argon2.hash(password);
}
