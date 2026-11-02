const { orderTotal, placeOrder } = require("../service/orderService");

function postOrder(id, total) {
  return `placed ${placeOrder(id, total).id}`;
}

function getTotal(id) {
  return `total ${orderTotal(id)}`;
}

module.exports = { postOrder, getTotal };
