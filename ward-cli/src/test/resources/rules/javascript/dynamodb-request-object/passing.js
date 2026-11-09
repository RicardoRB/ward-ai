const db = new AWS.DynamoDB.DocumentClient();
exports.handler = async (event) => {
  return db.put({ TableName: 'users', Item: { id: 1 } }).promise();
};
