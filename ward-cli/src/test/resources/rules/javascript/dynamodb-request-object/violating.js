const db = new AWS.DynamoDB.DocumentClient();
exports.handler = async (event) => {
  return db.put(event.body).promise();
};
