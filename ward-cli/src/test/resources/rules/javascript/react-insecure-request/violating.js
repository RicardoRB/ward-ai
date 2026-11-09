import axios from 'axios';
export function load() {
  return axios.get('http://example.com/data');
}
