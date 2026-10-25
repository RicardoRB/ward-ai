const { findById, save } = require("../repository/orderRepository");

function placeOrder(id, total) {
  if (total <= 0) {
    throw new Error("total must be positive");
  }
  const order = { id, total };
  save(order);
  return order;
}

function orderTotal(id) {
  const order = findById(id);
  if (order === undefined) {
    throw new Error(`unknown order ${id}`);
  }
  return order.total;
}

module.exports = { placeOrder, orderTotal };
