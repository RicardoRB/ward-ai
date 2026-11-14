const message = `data: ${JSON.stringify({ error: `failed: ${reason}` })}\n\n`
const deep = `a ${f({ b: `c ${g({ d: `e ${h}` })} i` })} j`
const mapped = `${[1, 2].map(n => { return n * 2 })}${ {k: 1}.k }`
