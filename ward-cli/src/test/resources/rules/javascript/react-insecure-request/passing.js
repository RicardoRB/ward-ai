import axios from 'axios';
export function load() {
  return axios.get('https://example.com/data');
}
