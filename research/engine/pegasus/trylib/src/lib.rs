use pegasus::api::Map;
use pegasus::BuildJobError;
use pegasus::stream::Stream;

#[no_mangle]
pub extern "Rust" fn build_job(input: Stream<Vec<u8>>) -> Result<Stream<Vec<u8>>, BuildJobError> {
    let worker_id = input.get_worker_id();
    pegasus::guard(worker_id);
    input.map(|mut bytes| {
        for b in bytes.iter_mut() {
            *b += 1;
        }
        Ok(bytes)
    })
}