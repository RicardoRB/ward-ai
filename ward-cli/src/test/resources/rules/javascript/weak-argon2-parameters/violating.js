import argon2 from 'argon2';

export async function cheapMemory(password) {
  return argon2.hash(password, { memoryCost: 4096, timeCost: 3 });
}

export async function cheapTime(password) {
  return argon2.hash(password, { memoryCost: 65536, timeCost: 1 });
}

export async function powerOfTwo(password) {
  return argon2.hash(password, { memoryCost: 2 ** 12 });
}
