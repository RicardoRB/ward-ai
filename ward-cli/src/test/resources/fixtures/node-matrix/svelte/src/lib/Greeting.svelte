<script lang="ts">
	import { greeting } from './greeting';

	let { name }: { name: string } = $props();
</script>

<p>{greeting(name)}</p>
